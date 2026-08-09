package io.github.heybadrinath.reconriver.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResultsServiceCsvTest {

    @Test
    void neutralizesSpreadsheetFormulasAndEscapesQuotes() {
        assertThat(ResultsService.csvText("=HYPERLINK(\"https://example.invalid\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"https://example.invalid\"\")\"");
        assertThat(ResultsService.csvText("+SUM(1,2)")).startsWith("\"'+");
        assertThat(ResultsService.csvText("-1+1")).startsWith("\"'-");
        assertThat(ResultsService.csvText("@cmd")).startsWith("\"'@");
        assertThat(ResultsService.csvText("safe value")).isEqualTo("\"safe value\"");
    }
}
