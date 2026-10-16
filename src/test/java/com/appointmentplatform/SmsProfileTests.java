package com.appointmentplatform;

import com.appointmentplatform.notification.SmsSender;
import com.appointmentplatform.notification.infrastructure.DevSmsSender;
import com.appointmentplatform.notification.infrastructure.UnavailableSmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

class SmsProfileTests {
    @Test
    void devLoggerIsNeverEnabledAlongsideProduction() {
        assertSender(DevSmsSender.class, "dev");
        assertSender(UnavailableSmsSender.class, "prod");
        assertSender(UnavailableSmsSender.class, "dev", "prod");
        assertSender(UnavailableSmsSender.class);
    }

    private void assertSender(Class<?> expected, String... profiles) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles);
            context.register(DevSmsSender.class, UnavailableSmsSender.class);
            context.refresh();
            assertThat(context.getBeansOfType(SmsSender.class)).hasSize(1);
            assertThat(context.getBean(SmsSender.class)).isInstanceOf(expected);
        }
    }
}
