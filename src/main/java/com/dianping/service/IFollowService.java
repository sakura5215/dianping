package com.dianping.service;

import com.dianping.dto.Result;
import com.dianping.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);

    /**
     * 查询用户的关注数与粉丝数
     * @param userId 目标用户
     * @return {followCount, fanCount}
     */
    Result followStat(Long userId);

    /**
     * 查询用户关注的人列表
     */
    Result followList(Long userId);

    /**
     * 查询用户的粉丝列表
     */
    Result fanList(Long userId);
}
