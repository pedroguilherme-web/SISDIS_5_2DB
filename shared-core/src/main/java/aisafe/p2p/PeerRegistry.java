
package aisafe.p2p;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PeerRegistry {

    private final Map<String, PeerNode> peers =
            new ConcurrentHashMap<>();

    public void registerPeer(PeerNode peer) {
        if (peer == null) {
            throw new IllegalArgumentException(
                    "Peer cannot be null");
        }

        peers.put(peer.getId(), peer);
    }

    public void removePeer(String peerId) {
        peers.remove(peerId);
    }

    public PeerNode getPeer(String peerId) {
        return peers.get(peerId);
    }

    public List<PeerNode> getAllPeers() {
        return List.copyOf(peers.values());
    }

    public int getPeerCount() {
        return peers.size();
    }
}
