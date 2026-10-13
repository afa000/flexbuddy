package com.angel.flexbuddy.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountDeletionRequestTest {

    @Test
    void theConfirmationIsTrimmedAndLowercased() {
        AccountDeletionRequest request = new AccountDeletionRequest();

        request.setConfirmation(" DELETE ");

        assertThat(request.getConfirmation()).isEqualTo("delete");
    }

    @Test
    void aMissingConfirmationStaysMissing() {
        AccountDeletionRequest request = new AccountDeletionRequest();

        request.setConfirmation(null);

        assertThat(request.getConfirmation()).isNull();
    }
}
