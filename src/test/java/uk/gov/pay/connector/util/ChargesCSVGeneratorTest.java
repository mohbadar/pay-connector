package uk.gov.pay.connector.util;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ChargesCSVGeneratorTest {

    private static final Map chargeMap = ImmutableMap.<String, String>builder()
            .put("reference", "ref")
            .put("charge_id", "100")
            .put("gateway_transaction_id", "200")
            .put("gateway_account_id", "300")
            .put("amount", "4.00")
            .put("created_date", "10/10/2016")
            .put("status", "Status")
            .put("payment_provider", "Provider")
            .put("description", "Description")
            .build();
    private static final Map chargeMap_2 = ImmutableMap.of(
            "reference", "ref-2",
            "charge_id", "chargeid-2",
            "gateway_transaction_id", "trans-2"
            );

    @Test
    public void testGenerateWithMappedHeaders() throws Exception {
        ArrayList<Map<String, Object>> objectMapList = new ArrayList<>();

        objectMapList.add(chargeMap);
        objectMapList.add(chargeMap_2);

        String generate = ChargesCSVGenerator.generate(objectMapList);
        String expectedOutput = "Service Payment Reference,Amount,Status,Gateway Transaction ID,GOV.UK Pay ID,Date Created\n" +
                                "ref,4.00,Status,200,100,10/10/2016\n" +
                                "ref-2,,,trans-2,chargeid-2,\n";
        
        assertEquals(expectedOutput, generate);
    }
}