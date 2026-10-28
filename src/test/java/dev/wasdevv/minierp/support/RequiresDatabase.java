package dev.wasdevv.minierp.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Disables an integration test class when {@code MINIERP_TEST_JDBC_URL} is not set, so the run reports
 * it as skipped instead of failing to start Spring or, worse, passing without having run.
 */
public class RequiresDatabase implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return TestDatabase.configured()
                ? ConditionEvaluationResult.enabled("MINIERP_TEST_JDBC_URL set")
                : ConditionEvaluationResult.disabled("no database: set MINIERP_TEST_JDBC_URL");
    }
}
