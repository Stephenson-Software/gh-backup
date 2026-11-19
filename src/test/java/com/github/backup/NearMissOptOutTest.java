package com.github.backup;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An opt-out written under a name Spring does not bind to {@value UsageReportingService#SWITCH} used to be
 * ignored, and reporting stayed on.
 */
class NearMissOptOutTest {

    /** An environment holding only the shipped defaults plus the given properties. */
    private static StandardEnvironment environment(Map<String, Object> file, Map<String, Object> env) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(new SystemEnvironmentPropertySource("env", env));
        environment.getPropertySources().addLast(new MapPropertySource("file", file));
        environment.getPropertySources().addLast(new MapPropertySource("defaults",
                Map.<String, Object>of(UsageReportingService.SWITCH, "true", "usage.reporting.endpoint", "http://127.0.0.1:9")));
        return environment;
    }

    private static Map<String, Object> one(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    @Test
    void anOptOutWrittenUnderANearMissNameIsAnOptOut() {
        Object[][] fileNearMisses = {
                {"usage-reporting.enabled", false},
                {"usageReporting.enabled", "off"},
                {"Usage_Reporting.Enabled", "false"},
                {"usage-reporting.enable", false},
                {"usage-reporting.disabled", true},
                {"usage-reporting.disable", "yes"},
                {"usage-reporting", false},
                {"usage_reporting", "no"},
        };
        for (Object[] nearMiss : fileNearMisses) {
            StandardEnvironment environment = environment(one((String) nearMiss[0], nearMiss[1]), Map.of());
            assertNotNull(NearMissOptOut.find(environment, UsageReportingService.SWITCH), nearMiss[0] + "=" + nearMiss[1]);
            assertEquals("false", UsageReportingService.switchOrNearMissOptOut("true", environment), nearMiss[0] + "=" + nearMiss[1]);
        }
        String[][] envNearMisses = {
                {"USAGE_REPORTING_DISABLED", "true"},
                {"USAGE_REPORTING_ENABLE", "false"},
                {"USAGE_REPORTING", "off"},
        };
        for (String[] nearMiss : envNearMisses) {
            StandardEnvironment environment = environment(Map.of(), one(nearMiss[0], nearMiss[1]));
            assertNotNull(NearMissOptOut.find(environment, UsageReportingService.SWITCH), nearMiss[0] + "=" + nearMiss[1]);
        }
    }

    @Test
    void nearMissNamesThatSayOnAreNotAnOptOut() {
        Object[][] onSettings = {
                {"usage-reporting.enable", true},
                {"usage-reporting.disabled", false},
                {"usageReporting", "on"},
                {"usage.reporting.endpoint", "http://127.0.0.1:9"},
                {"management.endpoints.enabled", false},
        };
        for (Object[] on : onSettings) {
            StandardEnvironment environment = environment(one((String) on[0], on[1]), Map.of());
            assertNull(NearMissOptOut.find(environment, UsageReportingService.SWITCH), on[0] + "=" + on[1]);
            assertEquals("true", UsageReportingService.switchOrNearMissOptOut("true", environment), on[0] + "=" + on[1]);
        }
        // The real switch is left to Spring, and the client reads TRACE_USAGE_REPORTING itself
        // (a blank one, as docker-compose passes through, is not an opt-out).
        assertNull(NearMissOptOut.find(environment(one(UsageReportingService.SWITCH, "true"),
                one("USAGE_REPORTING_ENABLED", "true")), UsageReportingService.SWITCH));
        assertNull(NearMissOptOut.find(environment(Map.of(), one("TRACE_USAGE_REPORTING", "")), UsageReportingService.SWITCH));
        assertNull(NearMissOptOut.find(environment(Map.of(), Map.of()), UsageReportingService.SWITCH));
    }

    @Test
    void theRealSwitchIsStillReadAsBefore() {
        StandardEnvironment environment = environment(Map.of(), Map.of());
        assertEquals("false", UsageReportingService.switchOrNearMissOptOut("false", environment));
        assertEquals("off", UsageReportingService.switchOrNearMissOptOut("off", environment));
        assertEquals("true", UsageReportingService.switchOrNearMissOptOut("true", environment));
    }
}
