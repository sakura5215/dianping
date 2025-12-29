package com.dianping.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dianping.dto.Result;
import com.dianping.dto.UserDTO;
import com.dianping.entity.Follow;
import com.dianping.mapper.FollowMapper;
import com.dianping.service.IFollowService;
import com.dianping.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.val;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    /**
     * 关注或取消关注
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        // 1. 判断是关注还是取消关注
        if (isFollow) {
            // 2. 关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 把关注用户的id保存到Redis的set集合 sadd userId followUserId
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }else{
            // 3. 取消关注 delete from follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess) {
                // 把关注用户的id从Redis的set集合移除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        // 1. 查询是否关注
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    /**
     * 共同关注：获取目标用户和当前用户的交集
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String keyCurrent = "follows:" + userId;
        String keyTarget = "follows:" + id;
        // 求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(keyCurrent, keyTarget);
        if (intersect == null || intersect.isEmpty()) {
            // 没有共同关注
            return Result.ok(Collections.emptyList());
        }
        // 共同关注列表
        List<Long> ids = intersect.stream().map(Long::valueOf).toList();
        // 根据id查询用户
        List<UserDTO> users = listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return Result.ok(users);
    }

    @Override
    public Result followStat(Long userId) {
        Long followCount = query().eq("user_id", userId).count();
        Long fanCount = query().eq("follow_user_id", userId).count();
        Map<String, Object> stat = new HashMap<>(4);
        stat.put("followCount", followCount);
        stat.put("fanCount", fanCount);
        return Result.ok(stat);
    }

    @Override
    public Result followList(Long userId) {
        // 我关注的人：tb_follow 里 user_id = 我的记录的 follow_user_id
        List<Long> ids = query().eq("user_id", userId).list().stream()
                .map(Follow::getFollowUserId).toList();
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return Result.ok(users);
    }

    @Override
    public Result fanList(Long userId) {
        // 我的粉丝：tb_follow 里 follow_user_id = 我的记录的 user_id
        List<Long> ids = query().eq("follow_user_id", userId).list().stream()
                .map(Follow::getUserId).toList();
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return Result.ok(users);
    }
}
