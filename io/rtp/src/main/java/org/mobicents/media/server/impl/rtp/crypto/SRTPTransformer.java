/**
 * 
 * Code derived and adapted from the Jitsi client side SRTP framework.
 * 
 * Distributed under LGPL license.
 * See terms of license at gnu.org.
 */
package org.mobicents.media.server.impl.rtp.crypto;

import java.nio.ByteBuffer;
import java.util.Hashtable;

import org.apache.log4j.Logger;
import org.mobicents.media.server.impl.rtp.RtpPacket;

/**
 * SRTPTransformer implements PacketTransformer and provides implementations for
 * RTP packet to SRTP packet transformation and SRTP packet to RTP packet
 * transformation logic.
 * 
 * It will first find the corresponding SRTPCryptoContext for each packet based
 * on their SSRC and then invoke the context object to perform the
 * transformation and reverse transformation operation.
 * 
 * @author Bing SU (nova.su@gmail.com)
 * @author ivelin.ivanov@telestax.com
 * 
 */
public class SRTPTransformer implements PacketTransformer {
	
	private static final Logger logger = Logger.getLogger(SRTPTransformer.class);
	
	private final RtpPacket packet;
	
	private SRTPTransformEngine forwardEngine;
	private SRTPTransformEngine reverseEngine;

	/**
	 * All the known SSRC's corresponding SRTPCryptoContexts
	 */
	private Hashtable<Long, SRTPCryptoContext> contexts;

	/**
	 * Constructs a SRTPTransformer object.
	 * 
	 * @param engine
	 *            The associated SRTPTransformEngine object for both transform
	 *            directions.
	 */
	public SRTPTransformer(SRTPTransformEngine engine) {
		this(engine, engine);
	}

	/**
	 * Constructs a SRTPTransformer object.
	 * 
	 * @param forwardEngine
	 *            The associated SRTPTransformEngine object for forward
	 *            transformations.
	 * @param reverseEngine
	 *            The associated SRTPTransformEngine object for reverse
	 *            transformations.
	 */
	public SRTPTransformer(SRTPTransformEngine forwardEngine, SRTPTransformEngine reverseEngine) {
		this.forwardEngine = forwardEngine;
		this.reverseEngine = reverseEngine;
		this.contexts = new Hashtable<Long, SRTPCryptoContext>();
		this.packet = new RtpPacket(RtpPacket.RTP_PACKET_MAX_SIZE, true);
	}

	/**
	 * Transforms a specific packet.
	 * 
	 * @param pkt
	 *            the packet to be transformed
	 * @return the transformed packet
	 */
	public boolean transform(byte[] pkt) {
		// Transform data into readable format
		ByteBuffer buffer = this.packet.getBuffer();
		buffer.clear();
		buffer.put(pkt, 0, pkt.length);
		buffer.flip();
		
		// Associate packet to a context
		long ssrc = packet.getSyncSource();
		SRTPCryptoContext context = contexts.get(ssrc);

		if (context == null) {
			context = forwardEngine.getDefaultContext().deriveContext(ssrc, 0, 0);
			context.deriveSrtpKeys(0);
			contexts.put(ssrc, context);
		}

		// Transform RTP packet into SRTP
		context.transformPacket(packet);
		return true;
	}

	/**
	 * Reverse-transforms a specific packet (i.e. transforms a transformed
	 * packet back).
	 * 
	 * @param pkt
	 *            the transformed packet to be restored
	 * @return the restored packet
	 */
	public boolean reverseTransform(byte[] pkt) {
		// Transform data into readable format
		ByteBuffer buffer = this.packet.getBuffer();
		buffer.clear();
		buffer.put(pkt, 0, pkt.length);
		buffer.flip();
		
		// only accept RTP version 2 (SNOM phones send weird packages when on
		// hold, ignore them with this check (RTP Version must be equal to 2)
		if (packet.getVersion() != 2) {
			return false;
		}

		long ssrc = packet.getSyncSource();
		SRTPCryptoContext context = contexts.get(ssrc);
		if (context == null) {
			context = reverseEngine.getDefaultContext().deriveContext(ssrc, 0, 0);
			context.deriveSrtpKeys(packet.getSeqNumber());
			contexts.put(ssrc, context);
		}

		return context.reverseTransformPacket(packet);
	}

	/**
	 * Close the transformer and underlying transform engine.
	 * 
	 * The close functions closes all stored crypto contexts. This deletes key
	 * data and forces a cleanup of the crypto contexts.
	 */
	public void close() {
		forwardEngine.close();
		if (forwardEngine != reverseEngine) {
			reverseEngine.close();
		}
		for (Long ssrc : contexts.keySet()) {
			SRTPCryptoContext context = contexts.get(ssrc);
			if (context != null) {
				context.close();
				contexts.remove(ssrc);
			}
		}
	}
}
