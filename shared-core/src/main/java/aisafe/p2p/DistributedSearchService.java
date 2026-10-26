
package aisafe.p2p;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DistributedSearchService {

    private final PeerRegistry registry;
    private final PeerClient client;
    private final SearchRequestTracker tracker;
    private final LocalSearchProvider localSearchProvider;

    // Mantém a compatibilidade com os testes existentes
    public DistributedSearchService(
            PeerRegistry registry,
            PeerClient client) {

        this(registry, client, path -> "[]");
    }

    // Construtor utilizado pela aplicação Spring Boot
    public DistributedSearchService(
            PeerRegistry registry,
            PeerClient client,
            LocalSearchProvider localSearchProvider) {

        this.registry = Objects.requireNonNull(registry);
        this.client = Objects.requireNonNull(client);
        this.localSearchProvider =
                Objects.requireNonNull(localSearchProvider);
        this.tracker = new SearchRequestTracker();
    }

    public List<String> searchAllPeers(String path) {

        List<String> results = new ArrayList<>();

        for (PeerNode peer : registry.getAllPeers()) {
            try {
                String response = client.searchPeer(peer, path);

                if (response != null) {
                    results.add(response);
                }

            } catch (Exception exception) {
                System.err.println(
                        "Peer unavailable: " + peer.getId()
                );
            }
        }

        return results;
    }

    public String searchAndAggregate(String path) {

        List<String> responses = searchAllPeers(path);

        JsonResponseAggregator aggregator =
                new JsonResponseAggregator();

        return aggregator.aggregate(responses);
    }

    public String processSearch(DistributedSearchRequest request) {

        Objects.requireNonNull(
                request, "Request cannot be null"
        );

        // Evita processar duas vezes o mesmo pedido
        if (!tracker.markIfNew(request.getRequestId())) {
            return "[]";
        }

        List<String> responses = new ArrayList<>();

        // 1. Pesquisa os dados locais deste servidor
        String localResponse =
                localSearchProvider.searchLocal(request.getPath());

        if (localResponse != null) {
            responses.add(localResponse);
        }

        // 2. Encaminha apenas se houver TTL disponível
        if (request.getTtl() > 0) {

            for (PeerNode peer : registry.getAllPeers()) {
                try {
                    String response =
                            client.forwardSearch(peer, request);

                    if (response != null) {
                        responses.add(response);
                    }

                } catch (Exception exception) {
                    System.err.println(
                            "Could not forward search to peer: "
                                    + peer.getId()
                    );
                }
            }
        }

        // 3. Agrega resultados locais e remotos
        JsonResponseAggregator aggregator =
                new JsonResponseAggregator();

        return aggregator.aggregate(responses);
    }
}
