package com.dianping.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.dto.LoginFormDTO;
import com.dianping.dto.Result;
import com.dianping.entity.User;
import jakarta.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     * @param phone 手机号
     * @return 发送结果
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     * @return 登录结果
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}
