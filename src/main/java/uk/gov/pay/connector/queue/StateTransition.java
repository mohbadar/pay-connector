package uk.gov.pay.connector.queue;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class StateTransition implements Delayed {
    private final Class stateTransitionEventClass;
    private final Long readTime;
    private final long delayDurationInMilliseconds;
    private final AtomicInteger attempts;

    private static final int MAXIMUM_NUMBER_OF_ATTEMPTS = 10;
    private static final int BASE_ATTEMPTS = 1;
    private static final long DEFAULT_DELAY_DURATION_IN_MILLISECONDS = 100L;

    public StateTransition(Class stateTransitionEventClass) {
        this(stateTransitionEventClass, BASE_ATTEMPTS, DEFAULT_DELAY_DURATION_IN_MILLISECONDS);
    }

    public StateTransition(Class stateTransitionEventClass, long delayDurationInMilliseconds) {
        this(stateTransitionEventClass, BASE_ATTEMPTS, delayDurationInMilliseconds);
    }

    public StateTransition(Class stateTransitionEventClass, int numberOfProcessAttempts, long delayDurationInMilliseconds) {
        this.stateTransitionEventClass = stateTransitionEventClass;
        this.attempts = new AtomicInteger(numberOfProcessAttempts);
        this.delayDurationInMilliseconds = delayDurationInMilliseconds;
        this.readTime = System.currentTimeMillis() + delayDurationInMilliseconds;
    }
    
    @Override
    public long getDelay(TimeUnit unit) {
        long diff = readTime - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return (int) (readTime - ((StateTransition) o).readTime);
    }

    public boolean shouldAttempt() {
        return attempts.intValue() < MAXIMUM_NUMBER_OF_ATTEMPTS;
    }

    public Class getStateTransitionEventClass() {
        return stateTransitionEventClass;
    }

    public Long getReadTime() {
        return readTime;
    }

    public long getDelayDurationInMilliseconds() {
        return delayDurationInMilliseconds;
    }

    public int getAttempts() {
        return attempts.intValue();
    }
    
    public abstract String getIdentifier();
    
    public abstract StateTransition getNext();
}
