package com.dianping.controller;


import com.dianping.dto.Result;
import com.dianping.service.IFollowService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id) {
        return followService.followCommons(id);
    }

    /**
     * 查询用户的关注数与粉丝数
     */
    @GetMapping("/count/{id}")
    public Result followStat(@PathVariable("id") Long userId) {
        return followService.followStat(userId);
    }

    /**
     * 查询用户关注的人列表
     */
    @GetMapping("/list/of/{id}")
    public Result followList(@PathVariable("id") Long userId) {
        return followService.followList(userId);
    }

    /**
     * 查询用户的粉丝列表
     */
    @GetMapping("/list/fan/{id}")
    public Result fanList(@PathVariable("id") Long userId) {
        return followService.fanList(userId);
    }
}
