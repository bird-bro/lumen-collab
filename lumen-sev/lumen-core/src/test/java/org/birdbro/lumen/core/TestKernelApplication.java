package org.birdbro.lumen.core;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TEST-SOURCES-ONLY boot host for the kernel's integration tests (design D9,
 * lumen-core-registration-kernel task 2.1). lumen-core itself is a NON-EXECUTABLE
 * shared library (service-topology Shared Library Boundary - the architecture guard
 * asserts no entry point in MAIN sources); this class exists in test sources so the
 * kernel's data-layer tests can boot a context against the shared test server exactly
 * the way a CONSUMING service will: {@code scanBasePackages = "org.birdbro"} registers
 * the framework/common shared beans (MetaObjectConfig audit fill among them) plus the
 * kernel's own services, and the {@code **.mapper} scan binds the kernel mappers -
 * both mirroring the consumer app shape (the OPS LaunchApplication habit).
 *
 * @author birdbro
 */
@SpringBootApplication(scanBasePackages = "org.birdbro")
@MapperScan(basePackages = "org.birdbro.**.mapper")
public class TestKernelApplication {
}
