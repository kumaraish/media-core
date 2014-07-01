package org.mobicents.media.core.ice;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.media.core.ice.harvest.HarvestException;
import org.mobicents.media.core.ice.harvest.NoCandidatesGatheredException;
import org.mobicents.media.core.ice.lite.LiteFoundationsRegistry;
import org.mobicents.media.server.io.network.PortManager;

import static org.junit.Assert.*;

/**
 * 
 * @author Henrique Rosa
 * 
 */
public class HostCandidateHarvesterTest {

	private IceMediaStream mediaStream;

	@Before
	public void before() {

	}

	@After
	public void after() {
		if (this.mediaStream != null) {
			closeMediaStream(mediaStream);
		}
	}

	private void closeMediaStream(IceMediaStream mediaStream) {
		IceComponent rtpComponent = mediaStream.getRtpComponent();
		for (LocalCandidateWrapper localCandidate : rtpComponent
				.getLocalCandidates()) {
			DatagramChannel channel = localCandidate.getChannel();
			if (channel.isOpen()) {
				try {
					channel.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Test
	public void testHostCandidateHarvesting() throws IOException {
		// given
		FoundationsRegistry foundationsRegistry = new LiteFoundationsRegistry();
		HostCandidateHarvester harvester = new HostCandidateHarvester(
				foundationsRegistry);
		IceMediaStream mediaStream = new IceMediaStream("audio", true);

		Selector selector = Selector.open();
		
		// when
		try {
			PortManager portManager = new PortManager();
			portManager.setHighestPort(62000);
			portManager.setLowestPort(61000);
			harvester.harvest(portManager, mediaStream, selector);
		} catch (NoCandidatesGatheredException e) {
			fail();
		} catch (HarvestException e) {
			fail();
		}

		// then
		List<LocalCandidateWrapper> rtpCandidates = mediaStream
				.getRtpComponent().getLocalCandidates();
		List<LocalCandidateWrapper> rtcpCandidates = mediaStream
				.getRtcpComponent().getLocalCandidates();

		assertTrue(rtpCandidates.size() > 0);
		assertTrue(rtcpCandidates.size() > 0);
		// Evaluate RTP Candidates
		for (LocalCandidateWrapper candidateWrapper : rtpCandidates) {
			DatagramChannel udpChannel = candidateWrapper.getChannel();
			assertFalse(udpChannel.isBlocking());
			assertFalse(udpChannel.isConnected());
			assertTrue(udpChannel.isOpen());

			IceCandidate candidate = candidateWrapper.getCandidate();
			assertEquals(candidate, candidate.getBase());
			assertEquals(new InetSocketAddress(candidate.getAddress(),
					candidate.getPort()), udpChannel.getLocalAddress());
			assertNotNull(udpChannel.keyFor(selector));
		}
		// Evaluate RTCP candidates
		for (LocalCandidateWrapper candidateWrapper : rtcpCandidates) {
			DatagramChannel udpChannel = candidateWrapper.getChannel();
			assertFalse(udpChannel.isBlocking());
			assertFalse(udpChannel.isConnected());
			assertTrue(udpChannel.isOpen());

			IceCandidate candidate = candidateWrapper.getCandidate();
			assertEquals(candidate, candidate.getBase());
			assertEquals(new InetSocketAddress(candidate.getAddress(),
					candidate.getPort()), udpChannel.getLocalAddress());
			assertNotNull(udpChannel.keyFor(selector));
		}
	}

	// TODO test port lookup for host candidate harvester
	public void testPortLookup() {
		// TODO Bind a socket to an address:port
		// TODO Tell harvester to search on that same port
		// TODO Assert binder binds to the next free port
	}
}