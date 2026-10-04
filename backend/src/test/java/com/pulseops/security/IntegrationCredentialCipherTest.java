package com.pulseops.security;
import com.pulseops.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class IntegrationCredentialCipherTest {
    final IntegrationCredentialCipher cipher=new IntegrationCredentialCipher("test-only-integration-encryption-secret-2026");
    @Test void encryptsAndDecryptsUnicodeWithDifferentNonces(){String first=cipher.encrypt("secret-credencial-á"),second=cipher.encrypt("secret-credencial-á");assertThat(first).isNotEqualTo(second).doesNotContain("credencial");assertThat(cipher.decrypt(first)).isEqualTo("secret-credencial-á");}
    @Test void rejectsTampering(){String encrypted=cipher.encrypt("secret");assertThatThrownBy(()->cipher.decrypt(encrypted.substring(0,encrypted.length()-5)+"AAAA")).isInstanceOf(BusinessRuleException.class);}
    @Test void rejectsChangedKey(){String encrypted=cipher.encrypt("secret");var changed=new IntegrationCredentialCipher("another-test-only-encryption-secret-2026");assertThatThrownBy(()->changed.decrypt(encrypted)).isInstanceOf(BusinessRuleException.class);}
    @Test void acceptsAbsentCredential(){assertThat(cipher.decrypt(null)).isNull();}
    @Test void rejectsMalformedCiphertext(){assertThatThrownBy(()->cipher.decrypt("invalid")).isInstanceOf(BusinessRuleException.class);}
    @Test void rejectsShortKeys(){assertThatThrownBy(()->new IntegrationCredentialCipher("short")).isInstanceOf(IllegalArgumentException.class);}
}
