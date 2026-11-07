package de.ark.support;

final class ApiError extends de.ark.accounts.api.AccountsApi.Failure {
    ApiError(int status, String code) { super(status,code); }
}
