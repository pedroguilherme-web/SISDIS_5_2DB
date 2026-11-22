package aisafe.routes.application;

import aisafe.routes.infrastructure.persistence.jpa.RouteReadViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RouteReadQueryServiceTest {

    @Mock
    private RouteReadViewRepository repository;

    @InjectMocks
    private RouteReadQueryService queryService;

    @Test
    void normalizesAndAppliesEveryProvidedFilter() {
        Pageable pageable = Pageable.unpaged();
        org.mockito.Mockito.when(repository.search("OPO", "LIS", "ACTIVE", pageable))
                .thenReturn(Page.empty(pageable));

        queryService.search(" opo ", "lis", " active ", pageable);

        verify(repository).search("OPO", "LIS", "ACTIVE", pageable);
    }
}
