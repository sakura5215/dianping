package com.dianping.service.impl;

import com.dianping.entity.BlogComments;
import com.dianping.mapper.BlogCommentsMapper;
import com.dianping.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
