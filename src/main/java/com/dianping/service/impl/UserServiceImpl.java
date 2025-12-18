package com.dianping.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.dto.LoginFormDTO;
import com.dianping.dto.Result;
import com.dianping.dto.UserDTO;
import com.dianping.entity.User;
import com.dianping.mapper.UserMapper;
import com.dianping.service.IUserService;
import com.dianping.utils.RedisConstants;
import com.dianping.utils.RegexUtils;
import com.dianping.utils.UserHolder;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.dianping.utils.RedisConstants.*;
import static com.dianping.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Service
@Slf4j
//这里serviceImpl是mybatisplus的，只用用query代表select * from tb_user，因为user有个注解@TableName("tb_user")
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //检验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("发送验证码成功，验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //检验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        //检验验证码,从redis中获取（验证码由 stringRedisTemplate 写入，须用同一模板读取，避免序列化器不一致导致读不到）
        String code = loginForm.getCode();
        Object cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            //不一致，返回错误
            return Result.fail("验证码错误");
        }

        //一致，查询用户是否存在
        //select * from tb_user where phone = ?
        //query代表select * from tb_user，因为user有个注解@TableName("tb_user"),one表示查询一个，查询多个的话用list
        User user = query().eq("phone", phone).one();

        //不存在，创建并保存
        if (user == null) {
            user =  createUserWithPhone(phone);
        }

        //保存用户信息到redis
        //随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString();

        //将User转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //由于使用StringRedisTemplate,但是UserDto里面存在integer，所以需要设置fieldValueEditor，转成 string
        Map<String, Object> userMap= BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .ignoreNullValue()
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        //设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //返回 token 给前端（前端 login.html 用 sessionStorage.setItem("token", data) 接收）
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.创建日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String key = USER_SIGN_KEY + userId+ now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // 4.获取今天是本月的第几天
        int day = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.创建日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String key = USER_SIGN_KEY + userId+ now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // 4.获取今天是本月的第几天
        int day = now.getDayOfMonth();
        // 5.获取这个月到今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(day))
                        .valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            //未签到
            return Result.ok(0);
        }
        Long number = result.get(0);
        if(number == null|| number == 0L){
            return Result.ok(0);
        }
        //6. 循环遍历
        int count = 0;
        while(true){
            // 6.1 让这个数字与1做与判断，得数字最后一个bit位
            if ((number & 1) == 0) {
                // 6.2 如果为0，说明未签到，结束
                break;
            }else{
                // 6.3 如果为1，说明已签到
                count++;
            }
            // 6.4 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            number = number >> 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //mp保存用户
        save(user);
        return user;
    }
}
