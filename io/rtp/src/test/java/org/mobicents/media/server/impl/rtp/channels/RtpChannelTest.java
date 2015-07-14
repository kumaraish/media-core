/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */
package org.mobicents.media.server.impl.rtp.channels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.media.server.impl.rtp.ChannelsManager;
import org.mobicents.media.server.impl.rtp.sdp.SdpFactory;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.io.sdp.fields.MediaDescriptionField;
import org.mobicents.media.server.scheduler.Clock;
import org.mobicents.media.server.scheduler.DefaultClock;
import org.mobicents.media.server.scheduler.Scheduler;

/**
 * 
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 *
 */
public class RtpChannelTest {
	
	private final Scheduler scheduler;
	private final UdpManager udpManager;
	private final ChannelsManager channelsManager;
	private final Clock wallClock;
	
	private final ChannelFactory factory;
	
	private final AudioChannel localChannel;
	private final AudioChannel remoteChannel;
	
	public RtpChannelTest() throws IOException {
		this.wallClock = new DefaultClock();
		this.scheduler = new Scheduler();
		this.scheduler.setClock(this.wallClock);
		this.udpManager = new UdpManager(this.scheduler);
		this.channelsManager = new ChannelsManager(udpManager);
		this.channelsManager.setScheduler(this.scheduler);
		
		this.factory = new ChannelFactory();
		this.localChannel = factory.buildAudioChannel();
		this.remoteChannel = factory.buildAudioChannel();
	}
	
	@Before
	public void before() throws InterruptedException {
		this.scheduler.start();
		this.udpManager.start();
	}
	
	@After
	public void after() {
		this.scheduler.stop();
		this.udpManager.stop();
		if(this.localChannel.isOpen()) {
			this.localChannel.close();
		}
		if(this.remoteChannel.isOpen()) {
			this.remoteChannel.close();
		}
	}

	@Test
	public void testSipCallNonRtcpMux() throws IllegalStateException, IOException, InterruptedException {
		/* GIVEN */
		boolean rtcpMux = false;
		
		/* WHEN */
		// activate local channel and bind it to local address
		// there will be two underlying channels for RTP and RTCP
		localChannel.open();
		localChannel.bind(false, false);
		
		String localAddress = localChannel.rtpChannel.getLocalHost();
		int localRtpPort = localChannel.rtpChannel.getLocalPort();
		int localRtcpPort = localChannel.rtcpChannel.getLocalPort(); 
		MediaDescriptionField audioOffer = SdpFactory.buildMediaDescription(localChannel);
		
		// activate "remote" channel and bind it to local address
		// there will be two underlying channels for RTP and RTCP
		remoteChannel.open();
		remoteChannel.bind(false, rtcpMux);
		
		String remoteAddress = remoteChannel.rtpChannel.getLocalHost();
		int remoteRtpPort = remoteChannel.rtpChannel.getLocalPort();
		int remoteRtcpPort = remoteChannel.rtcpChannel.getLocalPort();
		MediaDescriptionField audioAnswer = SdpFactory.buildMediaDescription(remoteChannel);
		
		// ... remote peer receives SDP offer from local peer
		// negotiate codecs with local peer
		remoteChannel.negotiateFormats(audioOffer);
		
		// connect to RTP and RTCP endpoints of local channel
		remoteChannel.connectRtp(localAddress, localRtpPort);
		remoteChannel.connectRtcp(localAddress, localRtcpPort);
		
		// ... local peer receives SDP answer from remote peer
		// negotiate codecs with remote peer
		localChannel.negotiateFormats(audioAnswer);

		// connect to RTP and RTCP endpoints of remote channel
		localChannel.connectRtp(remoteAddress, remoteRtpPort);
		localChannel.connectRtcp(remoteAddress, remoteRtcpPort);
		
		// THEN
		assertTrue(localChannel.isOpen());
		assertTrue(localChannel.isAvailable());
		assertFalse(localChannel.isRtcpMux());
		assertEquals(remoteAddress, localChannel.rtpChannel.getRemoteHost());
		assertEquals(remoteRtpPort, localChannel.rtpChannel.getRemotePort());
		assertEquals(remoteAddress, localChannel.rtcpChannel.getRemoteHost());
		assertEquals(remoteRtcpPort, localChannel.rtcpChannel.getRemotePort());

		assertTrue(remoteChannel.isOpen());
		assertTrue(remoteChannel.isAvailable());
		assertFalse(remoteChannel.isRtcpMux());
		assertEquals(localAddress, remoteChannel.rtpChannel.getRemoteHost());
		assertEquals(localRtpPort, remoteChannel.rtpChannel.getRemotePort());
		assertEquals(localAddress, remoteChannel.rtcpChannel.getRemoteHost());
		assertEquals(localRtcpPort, remoteChannel.rtcpChannel.getRemotePort());
	}

	@Test
	public void testSipCallWithRtcpMux() throws IllegalStateException, IOException, InterruptedException {
		/* GIVEN */
		boolean rtcpMux = true;
		
		/* WHEN */
		// activate local channel and bind it to local address
		// there will be two underlying channels for RTP and RTCP
		localChannel.open();
		localChannel.bind(false, rtcpMux);
		
		String localAddress = localChannel.rtpChannel.getLocalHost();
		int localPort = localChannel.rtpChannel.getLocalPort();
		MediaDescriptionField audioOffer = SdpFactory.buildMediaDescription(localChannel);
		
		// activate "remote" channel and bind it to local address
		// there will be two underlying channels for RTP and RTCP
		remoteChannel.open();
		remoteChannel.bind(false, rtcpMux);
		
		String remoteAddress = remoteChannel.rtpChannel.getLocalHost();
		int remotePort = remoteChannel.rtpChannel.getLocalPort();
		MediaDescriptionField audioAnswer = SdpFactory.buildMediaDescription(remoteChannel);
		
		// ... remote peer receives SDP offer from local peer
		// negotiate codecs with local peer
		remoteChannel.negotiateFormats(audioOffer);
		
		// connect to RTP and RTCP endpoints of local channel
		remoteChannel.connectRtp(localAddress, localPort);
		remoteChannel.connectRtcp(localAddress, localPort);
		
		// ... local peer receives SDP answer from remote peer
		// negotiate codecs with remote peer
		localChannel.negotiateFormats(audioAnswer);
		
		// connect to RTP and RTCP endpoints of remote channel
		localChannel.connectRtp(remoteAddress, remotePort);
		localChannel.connectRtcp(remoteAddress, remotePort);
		
		// THEN
		assertTrue(localChannel.isOpen());
		assertTrue(localChannel.isAvailable());
		assertTrue(localChannel.isRtcpMux());
		assertEquals(remoteAddress, localChannel.rtpChannel.getRemoteHost());
		assertEquals(remotePort, localChannel.rtpChannel.getRemotePort());
		assertFalse(localChannel.rtcpChannel.isOpen());
		
		assertTrue(remoteChannel.isOpen());
		assertTrue(remoteChannel.isAvailable());
		assertTrue(remoteChannel.isRtcpMux());
		assertEquals(localAddress, remoteChannel.rtpChannel.getRemoteHost());
		assertEquals(localPort, remoteChannel.rtpChannel.getRemotePort());
		assertFalse(remoteChannel.rtcpChannel.isOpen());
	}
	
	/**
	 * Produces Media Channels
	 * 
	 * @author Henrique Rosa
	 * 
	 */
	private class ChannelFactory {
		
		public AudioChannel buildAudioChannel() {
			return new AudioChannel(wallClock, channelsManager);
		}
		
	}
	
}