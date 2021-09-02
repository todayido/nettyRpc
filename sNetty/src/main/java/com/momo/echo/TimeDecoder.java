package com.momo.echo;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.util.List;

public class TimeDecoder extends ByteToMessageCodec {// (1)
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Object o, ByteBuf byteBuf) throws Exception {

    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List list) throws Exception {// (2)
        if (byteBuf.readableBytes() < 4) {
            return; // (3)
        }

        list.add(byteBuf.readBytes(4)); // (4)
    }

    /**
     * ByteToMessageDecoder is an implementation of ChannelInboundHandler which makes it easy to deal with the fragmentation issue.
     * ByteToMessageDecoder calls the decode() method with an internally maintained cumulative buffer whenever new data is received.
     * decode() can decide to add nothing to out when there is not enough data in the cumulative buffer.
     *      ByteToMessageDecoder will call decode() again when there is more data received.
     * If decode() adds an object to out, it means the decoder decoded a message successfully.
     *      ByteToMessageDecoder will discard the read part of the cumulative buffer.
     *      Please remember that you don't need to decode multiple messages.
     *      ByteToMessageDecoder will keep calling the decode() method until it adds nothing to out.
     */
}
