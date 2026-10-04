package com.pulseops.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulseops.dto.error.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest(HttpMethod.GET.name(), "/api/systems/missing");
    }

    @Test
    void shouldReturn404ForMissingDomainResource() {
        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(
                new ResourceNotFoundException("Monitored system", "system-42"), request);

        assertError(
                response,
                HttpStatus.NOT_FOUND,
                "Sistema não encontrado: system-42",
                "/api/systems/missing"
        );
    }

    @Test
    void shouldReturn409ForDomainConflict() {
        ResponseEntity<ApiErrorResponse> response = handler.handleConflict(
                new ConflictException("Já existe um incidente aberto"), request);

        assertError(response, HttpStatus.CONFLICT, "Já existe um incidente aberto", "/api/systems/missing");
    }

    @Test
    void shouldReturn422WhenBusinessRuleIsViolated() {
        ResponseEntity<ApiErrorResponse> response = handler.handleBusinessRule(
                new BusinessRuleException("Deploy não pode ser finalizado"), request);

        assertError(
                response,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Deploy não pode ser finalizado",
                "/api/systems/missing"
        );
    }

    @Test
    void shouldReturn422ForInvalidStateTransition() {
        InvalidStateTransitionException exception =
                new InvalidStateTransitionException("deployment", "SUCCESS", "RUNNING");

        ResponseEntity<ApiErrorResponse> response = handler.handleBusinessRule(exception, request);

        assertError(
                response,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Transição de deploy inválida: SUCCESS → RUNNING",
                "/api/systems/missing"
        );
    }

    @Test
    void shouldReturnDetailed400AndKeepFirstValidationMessageForEachField() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "payload");
        bindingResult.addError(new FieldError("payload", "email", "E-mail deve ser válido"));
        bindingResult.addError(new FieldError("payload", "email", "E-mail é obrigatório"));
        bindingResult.addError(new ObjectError("payload", "A combinação de campos é inválida"));
        MethodParameter methodParameter = new MethodParameter(
                ValidationFixture.class.getDeclaredMethod("accept", Object.class), 0);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).isEqualTo("Um ou mais campos são inválidos");
        assertThat(response.getBody().path()).isEqualTo("/api/systems/missing");
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().validationErrors())
                .hasSize(2)
                .containsEntry("email", "E-mail deve ser válido")
                .containsEntry("payload", "A combinação de campos é inválida");
    }

    @Test
    void shouldReturn400ForConstraintViolation() {
        ConstraintViolationException exception =
                new ConstraintViolationException("period: deve ser positivo", Set.of());

        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(exception, request);

        assertError(
                response,
                HttpStatus.BAD_REQUEST,
                "period: deve ser positivo",
                "/api/systems/missing"
        );
    }

    @Test
    void shouldReturn400ForMalformedRequestParameters() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("period", "String");

        ResponseEntity<ApiErrorResponse> response = handler.handleMalformedRequest(exception, request);

        assertError(
                response,
                HttpStatus.BAD_REQUEST,
                "A requisição possui formato ou parâmetros inválidos",
                "/api/systems/missing"
        );
    }

    @Test
    void shouldReturn409ForDatabaseIntegrityConflictWithoutLeakingDatabaseDetails() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("duplicate key value violates unique constraint users_email_key");

        ResponseEntity<ApiErrorResponse> response = handler.handleDataConflict(exception, request);

        assertError(
                response,
                HttpStatus.CONFLICT,
                "A operação viola uma restrição de integridade",
                "/api/systems/missing"
        );
        assertThat(response.getBody().message()).doesNotContain("users_email_key");
    }

    @Test
    void shouldReturn404ForUnknownHttpResource() {
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/unknown");

        ResponseEntity<ApiErrorResponse> response = handler.handleNoResource(exception, request);

        assertError(response, HttpStatus.NOT_FOUND, "Recurso não encontrado", "/api/systems/missing");
    }

    @Test
    void shouldReturn401ForInvalidCredentialsWithoutLeakingAuthenticationDetails() {
        BadCredentialsException exception = new BadCredentialsException("Password hash did not match");

        ResponseEntity<ApiErrorResponse> response = handler.handleBadCredentials(exception, request);

        assertError(response, HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos", "/api/systems/missing");
        assertThat(response.getBody().message()).doesNotContain("hash");
    }

    @Test
    void shouldReturn403ForDeniedAuthorization() {
        ResponseEntity<ApiErrorResponse> response = handler.handleAccessDenied(
                new AccessDeniedException("ADMIN role required"), request);

        assertError(
                response,
                HttpStatus.FORBIDDEN,
                "Acesso negado para este recurso",
                "/api/systems/missing"
        );
    }

    @Test
    void shouldReturnGeneric500WithoutLeakingUnexpectedFailureDetails() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("database-password=secret"), request);

        assertError(
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro interno inesperado",
                "/api/systems/missing"
        );
        assertThat(response.getBody().message()).doesNotContain("secret");
    }

    private void assertError(
            ResponseEntity<ApiErrorResponse> response,
            HttpStatus expectedStatus,
            String expectedMessage,
            String expectedPath
    ) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(expectedStatus.value());
        assertThat(response.getBody().error()).isEqualTo(expectedStatus.getReasonPhrase());
        assertThat(response.getBody().message()).isEqualTo(expectedMessage);
        assertThat(response.getBody().path()).isEqualTo(expectedPath);
        assertThat(response.getBody().validationErrors()).isNull();
    }

    private static final class ValidationFixture {

        @SuppressWarnings("unused")
        private void accept(Object payload) {
            // Only provides real method metadata required by MethodArgumentNotValidException.
        }
    }
}
