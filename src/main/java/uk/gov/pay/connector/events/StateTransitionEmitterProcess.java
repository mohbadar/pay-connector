package uk.gov.pay.connector.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.events.exception.EventCreationException;
import uk.gov.pay.connector.events.model.EventFactory;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.StateTransition;
import uk.gov.pay.connector.queue.StateTransitionQueue;

import javax.inject.Inject;
import java.util.Optional;

public class StateTransitionEmitterProcess {
    private static final Logger LOGGER = LoggerFactory.getLogger(StateTransitionEmitterProcess.class);

    private final StateTransitionQueue stateTransitionQueue;
    private final EventQueue eventQueue;
    private final EventFactory eventFactory;

    @Inject
    public StateTransitionEmitterProcess(
            StateTransitionQueue stateTransitionQueue,
            EventQueue eventQueue,
            EventFactory eventFactory
    ) {
        this.stateTransitionQueue = stateTransitionQueue;
        this.eventQueue = eventQueue;
        this.eventFactory = eventFactory;
    }

    public void handleStateTransitionMessages() {
        Optional.ofNullable(stateTransitionQueue.poll())
                .ifPresent(this::emitEvents);
    }

    private void emitEvents(StateTransition stateTransition) {
        if (stateTransition.shouldAttempt()) {
            try {
                eventFactory.createEvents(stateTransition)
                        .forEach(event -> {
                            try {
                                eventQueue.emitEvent(event);
                            } catch (QueueException e) {
                                handleException(e, stateTransition);
                            }
                        });
                LOGGER.info(
                        "Emitted new state transition event for [eventId={}] [eventType={}]",
                        stateTransition.getIdentifier(),
                        stateTransition.getStateTransitionEventClass().getSimpleName()
                );
            } catch (EventCreationException e) {
                handleException(e, stateTransition);
            }
        } else {
            LOGGER.error(
                    "State transition message failed to process beyond max retries [eventId={}] [eventType={}]:",
                    stateTransition.getIdentifier(),
                    stateTransition.getStateTransitionEventClass().getSimpleName()
            );
        }
    }

    private void handleException(Exception e, StateTransition stateTransition) {
        LOGGER.warn(
                "Failed to emit new event for state transition [eventId={}] [eventType={}] [error={}]",
                stateTransition.getIdentifier(),
                stateTransition.getStateTransitionEventClass().getSimpleName(),
                e.getMessage()
        );
        stateTransitionQueue.offer(stateTransition.getNext());
    }
}
