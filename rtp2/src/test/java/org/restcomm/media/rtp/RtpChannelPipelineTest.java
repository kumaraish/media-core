/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
 * by the @authors tag. 
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.restcomm.media.rtp;

import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Test;
import org.restcomm.media.pcap.GenericPcapReader;
import org.restcomm.media.pcap.PcapFile;
import org.restcomm.media.rtp.handler.RtpDemultiplexer;
import org.restcomm.media.rtp.handler.RtpInboundHandler;
import org.restcomm.media.rtp.handler.RtpInboundHandlerGlobalContext;
import org.restcomm.media.rtp.handler.RtpPacketEncoder;
import org.restcomm.media.rtp.jitter.JitterBuffer;
import org.restcomm.media.rtp.rfc2833.DtmfInput;
import org.restcomm.media.scheduler.Clock;
import org.restcomm.media.scheduler.WallClock;
import org.restcomm.media.sdp.format.AVProfile;
import org.restcomm.media.sdp.format.RTPFormat;
import org.restcomm.media.spi.ConnectionMode;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.ripe.hadoop.pcap.packet.Packet;

/**
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 *
 */
public class RtpChannelPipelineTest {

    private static final Logger log = Logger.getLogger(RtpChannelPipelineTest.class);

    private PcapFile pcapFile;

    @After
    public void after() {
        if (pcapFile != null) {
            if (!pcapFile.isComplete()) {
                try {
                    pcapFile.close();
                } catch (IOException e) {
                    log.warn("Could not close PCAP file " + pcapFile.getPath().toString(), e);
                }
            }
            pcapFile = null;
        }

    }

    @Test
    public void testIncomingRtpPacket() throws Exception {
        // given
        final Clock clock = new WallClock();
        final RtpStatistics statistics = mock(RtpStatistics.class);
        final JitterBuffer jitterBuffer = mock(JitterBuffer.class);
        final RtpInput rtpInput = mock(RtpInput.class);
        final DtmfInput dtmfInput = mock(DtmfInput.class);
        final RtpInboundHandlerGlobalContext context = new RtpInboundHandlerGlobalContext(clock, statistics, jitterBuffer, rtpInput, dtmfInput);
        final RtpInboundHandler inboundHandler = new RtpInboundHandler(context);
        final RtpDemultiplexer rtpDemultiplexer = new RtpDemultiplexer();
        final RtpPacketEncoder rtpPacketEncoder = new RtpPacketEncoder(statistics);
        final EmbeddedChannel channel = new EmbeddedChannel(rtpDemultiplexer, rtpPacketEncoder, inboundHandler);
        final PcapFile pcapFile = loadPcap("src/test/resources/pcap/rtp-packet.pcap");

        inboundHandler.activate();
        inboundHandler.updateMode(ConnectionMode.RECV_ONLY);
        inboundHandler.setFormatMap(AVProfile.audio);

        // when
        pcapFile.open();
        final Packet pcapPacket = pcapFile.read();
        final byte[] data = (byte[]) pcapPacket.get(GenericPcapReader.PAYLOAD);
        final ByteBuf buffer = Unpooled.wrappedBuffer(data);

        channel.writeInbound(buffer);
        pcapFile.close();

        // then
        verify(statistics).incomingRtp(any(RtpPacket.class));
        verify(statistics, never()).outgoingRtp(any(RtpPacket.class));
        verify(jitterBuffer).write(any(RtpPacket.class), any(RTPFormat.class));
    }

    @Test
    public void testOutgoingRtpPacket() throws Exception {
        // given
        final Clock clock = new WallClock();
        final RtpStatistics statistics = mock(RtpStatistics.class);
        final JitterBuffer jitterBuffer = mock(JitterBuffer.class);
        final RtpInput rtpInput = mock(RtpInput.class);
        final DtmfInput dtmfInput = mock(DtmfInput.class);
        final RtpInboundHandlerGlobalContext context = new RtpInboundHandlerGlobalContext(clock, statistics, jitterBuffer, rtpInput, dtmfInput);
        final RtpInboundHandler inboundHandler = new RtpInboundHandler(context);
        final RtpDemultiplexer rtpDemultiplexer = new RtpDemultiplexer();
        final RtpPacketEncoder rtpPacketEncoder = new RtpPacketEncoder(statistics);
        final EmbeddedChannel channel = new EmbeddedChannel(rtpDemultiplexer, rtpPacketEncoder, inboundHandler);
        final PcapFile pcapFile = loadPcap("src/test/resources/pcap/rtp-packet.pcap");
        
        inboundHandler.activate();
        inboundHandler.updateMode(ConnectionMode.SEND_ONLY);
        inboundHandler.setFormatMap(AVProfile.audio);
        
        // when
        pcapFile.open();
        final Packet pcapPacket = pcapFile.read();
        final byte[] data = (byte[]) pcapPacket.get(GenericPcapReader.PAYLOAD);
        final RtpPacket rtpPacket = new RtpPacket(data);
        
        channel.writeOutbound(rtpPacket);
        pcapFile.close();
        
        // then
        verify(statistics, never()).incomingRtp(any(RtpPacket.class));
        verify(statistics).outgoingRtp(any(RtpPacket.class));
    }

    @Test
    public void testLoopingRtpPacket() throws Exception {
        // given
        final Clock clock = new WallClock();
        final RtpStatistics statistics = mock(RtpStatistics.class);
        final JitterBuffer jitterBuffer = mock(JitterBuffer.class);
        final RtpInput rtpInput = mock(RtpInput.class);
        final DtmfInput dtmfInput = mock(DtmfInput.class);
        final RtpInboundHandlerGlobalContext context = new RtpInboundHandlerGlobalContext(clock, statistics, jitterBuffer, rtpInput, dtmfInput);
        final RtpInboundHandler inboundHandler = new RtpInboundHandler(context);
        final RtpDemultiplexer rtpDemultiplexer = new RtpDemultiplexer();
        final RtpPacketEncoder rtpPacketEncoder = new RtpPacketEncoder(statistics);
        final EmbeddedChannel channel = new EmbeddedChannel(rtpDemultiplexer, rtpPacketEncoder, inboundHandler);
        final PcapFile pcapFile = loadPcap("src/test/resources/pcap/rtp-packet.pcap");

        inboundHandler.activate();
        inboundHandler.updateMode(ConnectionMode.NETWORK_LOOPBACK);
        inboundHandler.setFormatMap(AVProfile.audio);

        // when
        pcapFile.open();
        final Packet pcapPacket = pcapFile.read();
        final byte[] data = (byte[]) pcapPacket.get(GenericPcapReader.PAYLOAD);
        final ByteBuf buffer = Unpooled.wrappedBuffer(data);

        channel.writeInbound(buffer);
        pcapFile.close();

        // then
        verify(statistics).incomingRtp(any(RtpPacket.class));
        verify(statistics).outgoingRtp(any(RtpPacket.class));
        verify(jitterBuffer).write(any(RtpPacket.class), any(RTPFormat.class));
    }

    private PcapFile loadPcap(String path) throws MalformedURLException {
        final File file = new File(path);
        final URL pcapUrl = file.toURI().toURL();
        PcapFile pcapFile = new PcapFile(pcapUrl);
        return pcapFile;
    }

}
