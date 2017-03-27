package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureProcessTest {

    CardCaptureProcess cardCaptureProcess;

    @Mock
    ChargeDao mockChargeDao;

    @Mock
    CardCaptureService mockCardCaptureService;

    @Mock
    Environment mockEnvironment;

    @Before
    public void setup() {
        MetricRegistry mockMetricRegistry = mock(MetricRegistry.class);
        Histogram mockHistogram = mock(Histogram.class);
        Counter mockCounter = mock(Counter.class);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        cardCaptureProcess = new CardCaptureProcess(mockEnvironment, mockChargeDao, mockCardCaptureService);
    }

    @Test
    public void shouldRetrieveASpecifiedNumberOfChargesApprovedForCapture() {
        ArgumentCaptor<ChargeSearchParams> searchParamsArgumentCaptor = ArgumentCaptor.forClass(ChargeSearchParams.class);

        cardCaptureProcess.runCapture();

        verify(mockChargeDao).findAllBy(searchParamsArgumentCaptor.capture());

        assertThat(searchParamsArgumentCaptor.getValue().getDisplaySize(),
                is(CardCaptureProcess.BATCH_SIZE));
        assertThat(searchParamsArgumentCaptor.getValue().getChargeStatuses(), hasItem(CAPTURE_APPROVED));
        assertThat(searchParamsArgumentCaptor.getValue().getPage(), is(1L));
    }

    @Test
    public void shouldRunCaptureForAllCharges() {
        ChargeEntity mockCharge1 = mock(ChargeEntity.class);
        ChargeEntity mockCharge2 = mock(ChargeEntity.class);

        when(mockChargeDao.findAllBy(any(ChargeSearchParams.class))).thenReturn(asList(mockCharge1, mockCharge2));
        when(mockCharge1.getExternalId()).thenReturn("my-charge-1");
        when(mockCharge2.getExternalId()).thenReturn("my-charge-2");

        cardCaptureProcess.runCapture();

        verify(mockCardCaptureService).doCapture("my-charge-1");
        verify(mockCardCaptureService).doCapture("my-charge-2");

    }
}