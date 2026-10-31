
package aisafe.p2p;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class P2PConfig {

    @Bean
    public PeerRegistry peerRegistry() {
        return new PeerRegistry();
    }

    @Bean
    public PeerIdentity peerIdentity(
            @Value("${p2p.peer-id}") String peerId) {

        return new PeerIdentity(peerId);
    }

    @Bean
    public PeerClient peerClient(
            PeerTokenService peerTokenService,
            PeerIdentity peerIdentity) {

        return new PeerClient(peerTokenService, peerIdentity);
    }

    @Bean
    public DistributedSearchService distributedSearchService(
            PeerRegistry registry,
            PeerClient client,
            LocalSearchProvider localSearchProvider) {

        return new DistributedSearchService(
                registry,
                client,
                localSearchProvider
        );
    }
}
