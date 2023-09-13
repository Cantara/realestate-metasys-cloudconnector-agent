package no.cantara.realestate.metasys.cloudconnector.stream;

import no.cantara.realestate.metasys.cloudconnector.automationserver.stream.MetasysObservedValueEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetasysObservedValueEventTest {
    String data ="""
    {
        "item": {
          "presentValue": 411.839996,
          "id": "05ccd193-a3f9-5db7-9c72-61987ca3d8dd",
          "itemReference": "metasysserver1:building2-434402-OS01/BACnet IP.E433_301-OU001.R3037.-RY601"
        },
        "condition": {
          "presentValue": {
            "reliability": "reliabilityEnumSet.reliable",
            "priority": "writePriorityEnumSet.priorityDefault"
          }
        }
      }
      """;

    @Test
    void parseObservedValueEvent() {
        MetasysObservedValueEvent event = new MetasysObservedValueEvent("id", "comment", data);
        assertEquals("05ccd193-a3f9-5db7-9c72-61987ca3d8dd", event.getObservedValue().getId());
        assertEquals("metasysserver1:building2-434402-OS01/BACnet IP.E433_301-OU001.R3037.-RY601", event.getObservedValue().getItemReference());
        assertEquals(411.839996, event.getObservedValue().getValue());
//        assertEquals("reliabilityEnumSet.reliable", event.getObservedValue().getCondition().getPresentValue().getReliability());
//        assertEquals("writePriorityEnumSet.priorityDefault", event.getObservedValue().getCondition().getPresentValue().getPriority());
    }

    @Test
    void parseObservedValueEventPresentValueIsString() {
        String data = """
                      {
                        "item": {
                          "presentValue": "binarypvEnumSet.bacbinActive",
                          "id": "61abb522-7173-57f6-9dc2-11e89d51aa54",
                          "itemReference": "metasysserver1:building2-434402-OS01/BACnet IP.E433_301-OU001.R3037.-RY601"
                        },
                        "condition": {
                          "presentValue": {
                            "reliability": "reliabilityEnumSet.reliable",
                            "priority": "writePriorityEnumSet.priorityDefault"
                          }
                        }
                      }
                """;
        MetasysObservedValueEvent event = new MetasysObservedValueEvent("id", "comment", data);
        assertEquals("61abb522-7173-57f6-9dc2-11e89d51aa54", event.getObservedValue().getId());
        assertEquals("metasysserver1:building2-434402-OS01/BACnet IP.E433_301-OU001.R3037.-RY601", event.getObservedValue().getItemReference());
        assertEquals("binarypvEnumSet.bacbinActive", event.getObservedValue().getValue());

    }
}