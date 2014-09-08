package org.mobicents.media.io.ice;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mobicents.media.io.ice.events.IceEventListener;
import org.mobicents.media.io.ice.events.SelectedCandidatesEvent;
import org.mobicents.media.io.ice.harvest.ExternalCandidateHarvester;
import org.mobicents.media.io.ice.harvest.HarvestException;
import org.mobicents.media.io.ice.harvest.HarvestManager;
import org.mobicents.media.io.ice.harvest.NoCandidatesGatheredException;
import org.mobicents.media.io.ice.network.stun.ConnectivityCheckServer;
import org.mobicents.media.server.io.network.PortManager;

public abstract class IceAgent implements IceAuthenticator {

	private final Map<String, IceMediaStream> mediaStreams;
	private final HarvestManager harvestManager;

	// Control message integrity
	protected final String ufrag;
	protected final String password;

	// Control stun checks
	protected Selector selector;
	protected ConnectivityCheckServer connectivityCheckServer;

	// Control selection process
	private volatile int selectedPairs;
	private volatile int maxSelectedPairs;

	// Control state of the agent
	protected volatile boolean running;

	// Delegate ICE-related events
	protected final List<IceEventListener> iceListeners;
	
	// External address where Media Server is installed
	// Required for fake SRFLX harvesting
	private InetAddress externalAddress;

	protected IceAgent() {
		this.mediaStreams = new LinkedHashMap<String, IceMediaStream>(5);
		this.harvestManager = new HarvestManager();

		this.iceListeners = new ArrayList<IceEventListener>(5);

		SecureRandom random = new SecureRandom();
		this.ufrag = new BigInteger(24, random).toString(32);
		this.password = new BigInteger(128, random).toString(32);

		this.selectedPairs = 0;
		this.maxSelectedPairs = 0;
		this.running = false;
	}

	/**
	 * Checks whether the Agent implements ICE Lite
	 * 
	 * @return true if the agent implements ICE Lite. False, in case of full
	 *         ICE.
	 */
	public abstract boolean isLite();

	/**
	 * Checks whether the Agent is controlling the ICE process.
	 * 
	 * @return
	 */
	public abstract boolean isControlling();

	/**
	 * Indicates whether the ICE agent is currently started.
	 * 
	 * @return
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Gets the local user fragment.
	 * 
	 * @return the local <code>ice-ufrag</code>
	 */
	public String getUfrag() {
		return ufrag;
	}

	/**
	 * Gets the password of the local user fragment
	 * 
	 * @return the local <code>ice-pwd</code>
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * Creates an <tt>IceMediaStream</tt> and adds to it an RTP and an RTCP
	 * components.
	 * 
	 * @param streamName
	 *            the name of the stream to create
	 * @param agent
	 *            the <tt>Agent</tt> that should create the stream.
	 * 
	 * @return the newly created <tt>IceMediaStream</tt>.
	 */
	public IceMediaStream addMediaStream(String streamName) {
		return addMediaStream(streamName, true);
	}

	/**
	 * Creates and registers a new media stream with an RTP component.<br>
	 * An secondary component may be created if the stream supports RTCP.
	 * 
	 * @param streamName
	 *            the name of the media stream
	 * @param rtcp
	 *            Indicates whether the media server supports RTCP.
	 * @return The newly created media stream.
	 */
	public IceMediaStream addMediaStream(String streamName, boolean rtcp) {
		if (!this.mediaStreams.containsKey(streamName)) {
			// Updates number of maximum allowed candidate pairs
			this.maxSelectedPairs += rtcp ? 2 : 1;
			// Register media stream
			return this.mediaStreams.put(streamName, new IceMediaStream(streamName, rtcp));
		}
		return null;
	}

	/**
	 * Gets a media stream by name
	 * 
	 * @param streamName
	 *            The name of the media stream
	 * @return The media stream. Returns null, if no media stream exists with
	 *         such name.
	 */
	public IceMediaStream getMediaStream(String streamName) {
		IceMediaStream mediaStream;
		synchronized (mediaStreams) {
			mediaStream = this.mediaStreams.get(streamName);
		}
		return mediaStream;
	}

	public List<IceMediaStream> getMediaStreams() {
		List<IceMediaStream> copy;
		synchronized (mediaStreams) {
			copy = new ArrayList<IceMediaStream>(this.mediaStreams.values());
		}
		return copy;
	}
	
	/**
	 * Gathers all available candidates and sets the components of each media
	 * stream.
	 * 
	 * @param portManager
	 *            The manager that handles port range for ICE candidate harvest
	 * @throws HarvestException
	 *             An error occurred while harvesting candidates
	 */
	public void gatherCandidates(PortManager portManager) throws HarvestException, NoCandidatesGatheredException {
		// Initialize the selector if necessary
		if (this.selector == null) {
			try {
				this.selector = Selector.open();
			} catch (IOException e) {
				throw new HarvestException("Could not initialize selector", e);
			}
		}

		// Gather candidates for each media stream
		for (IceMediaStream mediaStream : getMediaStreams()) {
			this.harvestManager.harvest(mediaStream, portManager, this.selector);
		}
	}

	/**
	 * Starts the ICE agent by activating its STUN stack.
	 * <p>
	 * <b>Full</b> ICE implementations start connectivity checks while listening
	 * for incoming checks.<br>
	 * <b>Lite</b> implementations are restricted to listen to incoming
	 * connectivity checks.
	 * </p>
	 */
	public abstract void start();

	/**
	 * Stops the ICE agent.
	 */
	public abstract void stop();

	public boolean isSelectionFinished() {
		return this.maxSelectedPairs == this.selectedPairs;
	}

	public CandidatePair selectCandidatePair(DatagramChannel channel) {
		CandidatePair candidatePair = null;
		for (IceMediaStream mediaStream : getMediaStreams()) {
			// Search for RTP candidates
			IceComponent rtpComponent = mediaStream.getRtpComponent();
			candidatePair = selectCandidatePair(rtpComponent, channel);
			if (candidatePair != null) {
				// candidate pair was selected
				break;
			}

			// Search for RTCP candidates (if supported by stream)
			if (candidatePair == null && mediaStream.supportsRtcp()) {
				IceComponent rtcpComponent = mediaStream.getRtcpComponent();
				candidatePair = selectCandidatePair(rtcpComponent, channel);
				if (candidatePair != null) {
					// candidate pair was selected
					break;
				}
			}
		}
		// IF found, increment number of selected candidate pairs
		if (candidatePair != null) {
			this.selectedPairs++;
		}

		// IF all candidates are selected, fire an event
		if (isSelectionFinished()) {
			fireCandidatePairSelectedEvent();
		}
		return candidatePair;
	}

	/**
	 * Attempts to select a candidate pair on a ICE component.<br>
	 * A candidate pair is only selected if the local candidate channel is
	 * registered with the provided Selection Key.
	 * 
	 * @param component
	 *            The component that holds the gathered candidates.
	 * @param key
	 *            The key of the datagram channel of the elected candidate.
	 * @return Returns the selected candidate pair. If no pair was selected,
	 *         returns null.
	 */
	private CandidatePair selectCandidatePair(IceComponent component, DatagramChannel channel) {
		for (LocalCandidateWrapper localCandidate : component.getLocalCandidates()) {
			if (channel.equals(localCandidate.getChannel())) {
				return component.setCandidatePair(channel);
			}
		}
		return null;
	}

	private CandidatePair getSelectedCandidate(String stream, int componentId) {
		// Find media stream
		IceMediaStream mediaStream = getMediaStream(stream);
		if(mediaStream != null) {
			// Find correct component
			IceComponent component;
			if(componentId == IceComponent.RTP_ID) {
				component = mediaStream.getRtpComponent();
			} else {
				component = mediaStream.getRtcpComponent();
			}
			
			// Get selected candidate from the component
			return component.getSelectedCandidates();
		}
		return null;
	}
	
	public CandidatePair getSelectedRtpCandidate(String stream) {
		return getSelectedCandidate(stream, IceComponent.RTP_ID);
	}

	public CandidatePair getSelectedRtcpCandidate(String stream) {
		return getSelectedCandidate(stream, IceComponent.RTCP_ID);
	}

	public void addIceListener(IceEventListener listener) {
		synchronized (this.iceListeners) {
			if (!this.iceListeners.contains(listener)) {
				this.iceListeners.add(listener);
			}
		}
	}

	public void removeIceListener(IceEventListener listener) {
		synchronized (this.iceListeners) {
			this.iceListeners.remove(listener);
		}
	}

	/**
	 * Fires an event when all candidate pairs are selected.
	 * 
	 * @param candidatePair
	 *            The selected candidate pair
	 */
	private void fireCandidatePairSelectedEvent() {
		// Stop the ICE Agent
		this.stop();

		// Fire the event to all listener
		List<IceEventListener> listeners;
		synchronized (this.iceListeners) {
			listeners = new ArrayList<IceEventListener>(this.iceListeners);
		}

		SelectedCandidatesEvent event = new SelectedCandidatesEvent(this);
		for (IceEventListener listener : listeners) {
			listener.onSelectedCandidates(event);
		}
	}

	public byte[] getLocalKey(String ufrag) {
		if (isUserRegistered(ufrag)) {
			if (this.password != null) {
				return this.password.getBytes();
			}
		}
		return null;
	}

	public byte[] getRemoteKey(String ufrag, String media) {
		// Verify if media stream exists
		IceMediaStream stream = getMediaStream(media);
		if (stream == null) {
			return null;
		}

		// Check whether full username is provided or just the fragment
		int colon = ufrag.indexOf(":");
		if (colon < 0) {
			if (ufrag.equals(stream.getRemoteUfrag())) {
				return stream.getRemotePassword().getBytes();
			}
		} else {
			if (ufrag.equals(getLocalUsername(stream))) {
				if (stream.getRemotePassword() != null) {
					return stream.getRemotePassword().getBytes();
				}
			}
		}
		return null;
	}

	/**
	 * Returns the user name that the ICE Agent should use in connectivity
	 * checks for outgoing Binding Requests. According to RFC 5245, a Binding
	 * Request serving as a connectivity check MUST utilize the STUN short term
	 * credential mechanism. The username for the credential is formed by
	 * concatenating the username fragment provided by the peer with the
	 * username fragment of the agent sending the request, separated by a colon
	 * (":"). The password is equal to the password provided by the peer. For
	 * example, consider the case where agent L is the offerer, and agent R is
	 * the answerer. Agent L included a username fragment of LFRAG for its
	 * candidates, and a password of LPASS. Agent R provided a username fragment
	 * of RFRAG and a password of RPASS. A connectivity check from L to R (and
	 * its response of course) utilize the username RFRAG:LFRAG and a password
	 * of RPASS. A connectivity check from R to L (and its response) utilize the
	 * username LFRAG:RFRAG and a password of LPASS.
	 * 
	 * @param media
	 *            media name that we want to generate local username for.
	 * @return a user name that this <tt>Agent</tt> can use in connectivity
	 *         check for outgoing Binding Requests.
	 */
	private String getLocalUsername(IceMediaStream stream) {
		if (stream != null) {
			if (stream.getRemotePassword() != null) {
				return this.ufrag + ":" + stream.getRemotePassword();
			}
		}
		return null;

	}

	public boolean isUserRegistered(String ufrag) {
		int colon = ufrag.indexOf(":");
		String result = colon < 0 ? ufrag : ufrag.substring(0, colon);
		return result.equals(this.ufrag);
	}
	
	public InetAddress getExternalAddress() {
		return externalAddress;
	}
	
	public void setExternalAddress(final InetAddress externalAddress) {
		this.externalAddress = externalAddress;
		
		// register an SRFLX harvester
		this.harvestManager.addHarvester(new ExternalCandidateHarvester(harvestManager.getFoundationsRegistry(), externalAddress));
	}
	
}
