package system;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * End-to-end suite: runs the two Sender user journeys ({@code system/*.feature}) against the whole
 * dockerized system, driving every step through the api-gateway (the only entrypoint).
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("system")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "system.steps")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
public class RunCucumberTest {
}
