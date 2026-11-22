package aisafe.flights.infrastructure.messaging;

import aisafe.flights.infrastructure.persistence.jpa.outbox.OutboxMessageEntity;
import aisafe.flights.infrastructure.persistence.jpa.outbox.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {
    @Mock private OutboxMessageRepository outbox;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private RabbitOperations operations;
    @Mock private PlatformTransactionManager transactionManager;

    private OutboxRelay relay;
    private OutboxMessageEntity first;
    private OutboxMessageEntity second;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        relay = new OutboxRelay(outbox, rabbitTemplate, transactionManager, "flight-operations-1");
        first = new OutboxMessageEntity("m-1", 1, "aisafe.exchange", "flight.status.changed", "FLIGHT_STATUS_CHANGED",
                "{}".getBytes(StandardCharsets.UTF_8));
        second = new OutboxMessageEntity("m-2", 2, "aisafe.exchange", "flight.completed", "FLIGHT_COMPLETED",
                "{}".getBytes(StandardCharsets.UTF_8));
        lenient().when(outbox.findById("m-1")).thenReturn(Optional.of(first));
        lenient().when(outbox.findById("m-2")).thenReturn(Optional.of(second));
        lenient().when(rabbitTemplate.invoke(any())).thenAnswer(invocation ->
                invocation.<RabbitOperations.OperationsCallback<?>>getArgument(0).doInRabbit(operations));
    }

    @Test
    void shouldPublishInOrderWaitForConfirmsAndMarkAsPublished() {
        when(outbox.findTop100ByPublishedAtIsNullOrderBySequenceNumberAsc()).thenReturn(List.of(first, second));

        assertEquals(2, relay.publishPending());

        var order = inOrder(operations);
        order.verify(operations).send(eq("aisafe.exchange"), eq("flight.status.changed"), any(Message.class));
        order.verify(operations).waitForConfirmsOrDie(anyLong());
        order.verify(operations).send(eq("aisafe.exchange"), eq("flight.completed"), any(Message.class));
        assertNotNull(first.getPublishedAt());
        assertNotNull(second.getPublishedAt());
    }

    @Test
    void shouldStopAtTheFirstFailureSoTheOrderIsNeverBroken() {
        when(outbox.findTop100ByPublishedAtIsNullOrderBySequenceNumberAsc()).thenReturn(List.of(first, second));
        doThrow(new AmqpConnectException(new ConnectException("refused")))
                .when(operations).send(anyString(), eq("flight.status.changed"), any(Message.class));

        assertEquals(0, relay.publishPending());

        verify(operations, never()).send(anyString(), eq("flight.completed"), any(Message.class));
        assertNull(first.getPublishedAt());
        assertEquals(1, first.getAttempts());
        assertTrue(first.getLastError().contains("refused"));
    }

    @Test
    void shouldCarryTheMessageIdAndTheSourceInstance() {
        when(outbox.findTop100ByPublishedAtIsNullOrderBySequenceNumberAsc()).thenReturn(List.of(first));

        relay.publishPending();

        var message = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(operations).send(anyString(), anyString(), message.capture());
        assertEquals("m-1", message.getValue().getMessageProperties().getMessageId());
        assertEquals("application/json", message.getValue().getMessageProperties().getContentType());
        assertEquals("flight-operations-1", message.getValue().getMessageProperties().getHeader(MessagingTopology.HEADER_SOURCE_INSTANCE));
    }
}
