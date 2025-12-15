package com.dianping.service.impl;

import com.dianping.entity.UserInfo;
import com.dianping.mapper.UserInfoMapper;
import com.dianping.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
