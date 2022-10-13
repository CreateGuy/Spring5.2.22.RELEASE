package com.lzx.demo;

import org.springframework.stereotype.Service;

/**
 * @author ber
 * @version 1.0
 * @date 21/11/9 13:13
 */
@Service("msg")
public class MsgServiceImpl implements MsgService {
	@Override
	public String getMsg() {
		return "Hello Ber!";
	}
}

