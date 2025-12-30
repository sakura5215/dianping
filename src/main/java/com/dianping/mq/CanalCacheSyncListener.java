package com.dianping.mq;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.dianping.entity.Shop;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.List;

import static com.dianping.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * Canal binlog 订阅监听器：异步失效缓存，解决缓存与数据库一致性。
 *
 * 背景：原先 update 走 Cache-Aside「双删」（应用主动删 Redis + Caffeine），
 * 但依赖业务代码记得删，且删的动作和 DB 提交之间仍有窗口，一致性不彻底。
 * 现在改成订阅 MySQL binlog：Canal 伪装成 MySQL 从库，把 tb_shop 的变更
 * 以事件流推过来，本监听器消费后被动失效缓存——即使有别的服务/脚本直接改库，
 * 缓存也能被同步失效，做到最终一致。
 *
 * 只订阅 tb_shop（当前唯一有缓存一致性诉求的表）。
 */
@Component
@Slf4j
public class CanalCacheSyncListener {

    private static final String DESTINATION = "example";   // Canal instance 名
    private static final String SUBSCRIBE = "dianping\\.tb_shop"; // 订阅库表

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private Cache<Long, Shop> shopLocalCache;

    @Value("${dianping.canal.host:localhost}")
    private String canalHost;

    @Value("${dianping.canal.port:11111}")
    private int canalPort;

    @Value("${dianping.canal.enabled:true}")
    private boolean enabled;

    private CanalConnector connector;
    private volatile boolean running = true;
    private Thread worker;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("[Canal] 缓存同步监听已禁用（dianping.canal.enabled=false）");
            return;
        }
        connector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(canalHost, canalPort), DESTINATION, "", "");
        worker = new Thread(this::run, "canal-cache-sync");
        worker.setDaemon(true);
        worker.start();
        log.info("[Canal] 缓存同步监听启动，订阅 {}:{}", DESTINATION, SUBSCRIBE);
    }

    private void run() {
        int emptyCount = 0;
        try {
            connector.connect();
            connector.subscribe(SUBSCRIBE);
            connector.rollback(); // 从当前位点开始，历史数据不回溯
            while (running) {
                // 每次拉 1000 条，最多等待 3s
                Message message = connector.getWithoutAck(1000, 3L, java.util.concurrent.TimeUnit.SECONDS);
                long batchId = message.getId();
                int size = message.getEntries().size();
                if (batchId == -1 || size == 0) {
                    emptyCount++;
                    // 连续 60 次空轮询（约 3 分钟）不触发，避免刷屏
                    if (emptyCount % 60 == 0) {
                        log.debug("[Canal] 暂无变更，心跳存活");
                    }
                    continue;
                }
                emptyCount = 0;
                try {
                    handleEntries(message.getEntries());
                    connector.ack(batchId); // 处理成功才 ack
                } catch (Exception e) {
                    log.error("[Canal] 处理 binlog 失败，batchId={}，将回滚重试", batchId, e);
                    connector.rollback(batchId); // 失败回滚，下次重拉
                }
            }
        } catch (Exception e) {
            log.error("[Canal] 监听线程异常退出", e);
        } finally {
            if (connector != null) {
                connector.disconnect();
            }
        }
    }

    private void handleEntries(List<CanalEntry.Entry> entries) {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                continue;
            }
            CanalEntry.RowChange rowChange;
            try {
                rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                log.error("[Canal] 解析 RowChange 失败", e);
                continue;
            }
            // 只关心 shop 表，其它表忽略
            if (!"tb_shop".equalsIgnoreCase(entry.getHeader().getTableName())) {
                continue;
            }
            CanalEntry.EventType eventType = rowChange.getEventType();
            // 只处理更新/删除（插入不影响已有缓存，且插入后首次查询会回源并写缓存）
            if (eventType != CanalEntry.EventType.UPDATE && eventType != CanalEntry.EventType.DELETE) {
                continue;
            }
            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                // 变更前的行（UPDATE/DELETE 都在 before 里有主键）
                List<CanalEntry.Column> beforeColumns = rowData.getBeforeColumnsList();
                Long shopId = extractShopId(beforeColumns);
                if (shopId == null) {
                    continue;
                }
                invalidateCache(shopId);
            }
        }
    }

    private Long extractShopId(List<CanalEntry.Column> columns) {
        for (CanalEntry.Column column : columns) {
            if ("id".equalsIgnoreCase(column.getName())) {
                return Long.valueOf(column.getValue());
            }
        }
        return null;
    }

    private void invalidateCache(Long shopId) {
        // 失效 Redis L2
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shopId);
        // 失效 Caffeine L1
        shopLocalCache.invalidate(shopId);
        log.info("[Canal] 检测到 tb_shop.id={} 变更，已失效多级缓存", shopId);
    }

    @PreDestroy
    public void destroy() {
        running = false;
        if (connector != null) {
            try {
                connector.disconnect();
            } catch (Exception ignored) {
            }
        }
    }
}
