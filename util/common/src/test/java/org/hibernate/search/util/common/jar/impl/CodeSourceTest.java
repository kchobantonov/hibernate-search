/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.util.common.jar.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.search.util.impl.test.jar.JarTestUtils.toJar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.hibernate.search.util.common.impl.Closer;
import org.hibernate.search.util.impl.test.SystemHelper;
import org.hibernate.search.util.impl.test.annotation.TestForIssue;
import org.hibernate.search.util.impl.test.function.ThrowingConsumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.loader.net.protocol.Handlers;
import org.springframework.boot.loader.net.protocol.jar.JarUrl;

class CodeSourceTest {
	private static final String PROTOCOL_HANDLER_PACKAGES = "java.protocol.handler.pkgs";
	private static final String META_INF_FILE_RELATIVE_PATH = "META-INF/someFile.txt";
	private static final byte[] META_INF_FILE_CONTENT = "This is some content".getBytes( StandardCharsets.UTF_8 );
	private static final String NON_EXISTING_FILE_RELATIVE_PATH = "META-INF/nonExisting.txt";
	private static final String SIMPLE_CLASS_RELATIVE_PATH = SimpleClass.class.getName().replaceAll(
			"\\.", "/" ) + ".class";
	@TempDir
	public Path temporaryFolder;

	private final List<SystemHelper.SystemPropertyRestorer> toClose = new ArrayList<>();

	@BeforeEach
	void setupHandlers() {
		// Spring has some of their own handlers configured through this property.
		// Handlers.register(); is required to get them registered.
		// We want to have them available for tests that rely on Spring Boot.
		// We don't want them to interfere with non-spring tests, so we reset the property before running a test.
		// We will rely on their own registration mechanism, but we want to reset the property back to the system value once we are done with the tests.
		// We just set the value to the current one, so it gets registered in the helper:
		toClose.add( SystemHelper.setSystemProperty( PROTOCOL_HANDLER_PACKAGES,
				System.getProperty( PROTOCOL_HANDLER_PACKAGES, "" ) ) );
	}

	@AfterEach
	void restoreSystemProperties() {
		try ( Closer<RuntimeException> closer = new Closer<>() ) {
			closer.pushAll( SystemHelper.SystemPropertyRestorer::close, toClose );
		}
	}

	@Test
	void directory() throws Exception {
		Path dirPath = createDir( root -> {
			addMetaInfFile( root );
			addSimpleClass( root );
		} );

		URL dirPathUrl = dirPath.toUri().toURL();
		try ( URLClassLoader isolatedClassLoader = createIsolatedClassLoader( dirPathUrl ) ) {
			Class<?> classInIsolatedClassLoader = isolatedClassLoader.loadClass( SimpleClass.class.getName() );

			// Check preconditions: this is the situation that we want to test.
			URL location = classInIsolatedClassLoader.getProtectionDomain().getCodeSource().getLocation();
			assertThat( location.getProtocol() ).isEqualTo( "file" );
			assertThat( location.toExternalForm() ).contains( dirPathUrl.toString() );

			// Check that the JAR can be opened and that we can access other files within it
			try ( CodeSource codeSource = new CodeSource( location ) ) {
				try ( InputStream is = codeSource.readOrNull( META_INF_FILE_RELATIVE_PATH ) ) {
					assertThat( is ).hasBinaryContent( META_INF_FILE_CONTENT );
				}
				try ( InputStream is = codeSource.readOrNull( NON_EXISTING_FILE_RELATIVE_PATH ) ) {
					assertThat( is ).isNull();
				}
				Path classesPath = codeSource.classesPathOrFail();
				try ( Stream<Path> files = Files.walk( classesPath ).filter( Files::isRegularFile ) ) {
					assertThat( files )
							.containsExactlyInAnyOrder(
									classesPath.resolve( META_INF_FILE_RELATIVE_PATH ),
									classesPath.resolve( SIMPLE_CLASS_RELATIVE_PATH )
							);
				}
			}
		}
	}

	@Test
	void jar_fileScheme() throws Exception {
		Path jarPath = createJar( root -> {
			addMetaInfFile( root );
			addSimpleClass( root );
		} );

		URL jarPathUrl = jarPath.toUri().toURL();
		try ( URLClassLoader isolatedClassLoader = createIsolatedClassLoader( jarPathUrl ) ) {
			Class<?> classInIsolatedClassLoader = isolatedClassLoader.loadClass( SimpleClass.class.getName() );

			// Check preconditions: this is the situation that we want to test.
			URL location = classInIsolatedClassLoader.getProtectionDomain().getCodeSource().getLocation();
			assertThat( location.getProtocol() ).isEqualTo( "file" );
			assertThat( location.toExternalForm() ).contains( jarPathUrl.toString() );

			// Check that the JAR can be opened and that we can access other files within it
			try ( CodeSource codeSource = new CodeSource( location ) ) {
				try ( InputStream is = codeSource.readOrNull( META_INF_FILE_RELATIVE_PATH ) ) {
					assertThat( is ).hasBinaryContent( META_INF_FILE_CONTENT );
				}
				try ( InputStream is = codeSource.readOrNull( NON_EXISTING_FILE_RELATIVE_PATH ) ) {
					assertThat( is ).isNull();
				}
				Path classesPath = codeSource.classesPathOrFail();
				try ( Stream<Path> files = Files.walk( classesPath ).filter( Files::isRegularFile ) ) {
					assertThat( files )
							.containsExactlyInAnyOrder(
									classesPath.resolve( META_INF_FILE_RELATIVE_PATH ),
									classesPath.resolve( SIMPLE_CLASS_RELATIVE_PATH )
							);
				}
			}
		}
	}

	@Test
	void jar_jarScheme_classesInRoot() throws Exception {
		Path jarPath = createJar( root -> {
			addMetaInfFile( root );
			addSimpleClass( root );
		} );

		URI fileURL = jarPath.toUri();
		URL jarURL = new URI( "jar:" + fileURL + "!/" ).toURL();
		try ( URLClassLoader isolatedClassLoader = createIsolatedClassLoader( jarURL ) ) {
			Class<?> classInIsolatedClassLoader = isolatedClassLoader.loadClass( SimpleClass.class.getName() );

			// Check preconditions: this is the situation that we want to test.
			URL location = classInIsolatedClassLoader.getProtectionDomain().getCodeSource().getLocation();
			// For some reason the "jar" scheme gets replaced with "file"
			assertThat( location.getProtocol() ).isEqualTo( "file" );
			assertThat( location.toExternalForm() ).contains( jarPath.toUri().toURL().toString() );

			// Check that the JAR can be opened and that we can access other files within it
			try ( CodeSource codeSource = new CodeSource( location ) ) {
				try ( InputStream is = codeSource.readOrNull( META_INF_FILE_RELATIVE_PATH ) ) {
					assertThat( is ).hasBinaryContent( META_INF_FILE_CONTENT );
				}
				try ( InputStream is = codeSource.readOrNull( NON_EXISTING_FILE_RELATIVE_PATH ) ) {
					assertThat( is ).isNull();
				}
				Path classesPath = codeSource.classesPathOrFail();
				try ( Stream<Path> files = Files.walk( classesPath ).filter( Files::isRegularFile ) ) {
					assertThat( files )
							.containsExactlyInAnyOrder(
									classesPath.resolve( META_INF_FILE_RELATIVE_PATH ),
									classesPath.resolve( SIMPLE_CLASS_RELATIVE_PATH )
							);
				}
			}
		}
	}

	// Spring Boot, through its maven plugin, offers a peculiar JAR structure backed by a custom URL handler.
	// This tests that we correctly detect the path to the JAR in that case anyway.
	// See https://docs.spring.io/spring-boot/docs/2.2.13.RELEASE/maven-plugin//repackage-mojo.html
	@Test
	@TestForIssue(jiraKey = "HSEARCH-4724")
	void jar_jarScheme_springBoot_classesInSubDirectory() throws Exception {
		Handlers.register();
		String classesDirRelativeString = "BOOT-INF/classes/";
		Path jarPath = createJar( root -> {
			addMetaInfFile( root );
			addSimpleClass( root.resolve( classesDirRelativeString ) );
		} );

		try ( JarFile outerJar = new JarFile( jarPath.toFile() ) ) {
			URL innerJarURL = JarUrl.create( jarPath.toFile(), null, classesDirRelativeString );
			try ( URLClassLoader isolatedClassLoader = createIsolatedClassLoader( innerJarURL ) ) {
				Class<?> classInIsolatedClassLoader = isolatedClassLoader.loadClass( SimpleClass.class.getName() );

				// Check preconditions: this is the situation that we want to test.
				URL location = classInIsolatedClassLoader.getProtectionDomain().getCodeSource().getLocation();
				assertThat( location.getProtocol() ).isEqualTo( "jar" );
				assertThat( location.toExternalForm() ).contains( classesDirRelativeString );

				// Check that the JAR can be opened and that we can access other files within it
				try ( CodeSource codeSource = new CodeSource( location ) ) {
					try ( InputStream is = codeSource.readOrNull( META_INF_FILE_RELATIVE_PATH ) ) {
						assertThat( is ).hasBinaryContent( META_INF_FILE_CONTENT );
					}
					try ( InputStream is = codeSource.readOrNull( NON_EXISTING_FILE_RELATIVE_PATH ) ) {
						assertThat( is ).isNull();
					}
					Path classesPath = codeSource.classesPathOrFail();
					Path jarRoot = classesPath.getRoot();
					try ( Stream<Path> files = Files.walk( jarRoot ).filter( Files::isRegularFile ) ) {
						assertThat( files )
								.containsExactlyInAnyOrder(
										jarRoot.resolve( META_INF_FILE_RELATIVE_PATH ),
										classesPath.resolve( SIMPLE_CLASS_RELATIVE_PATH )
								);
					}
				}
			}
		}
	}

	// Spring Boot, through its maven plugin, offers a peculiar JAR structure backed by a custom URL handler.
	// This tests that we correctly detect the path to the (outer) JAR in that case anyway,
	// and can retrieve content from that JAR.
	// See https://docs.spring.io/spring-boot/docs/2.2.13.RELEASE/maven-plugin//repackage-mojo.html
	@Test
	@TestForIssue(jiraKey = "HSEARCH-4724")
	void jar_jarScheme_springBoot_classesInSubJarInSubDirectory() throws Exception {
		Handlers.register();
		String innerJarInOuterJarRelativePathString = "BOOT-INF/lib/inner.jar";
		// For some reason inner JAR entries in the outer JAR must not be compressed, otherwise classloading will fail.
		Path outerJarPath = createJar(
				Collections.singletonMap( "compressionMethod", "STORED" ),
				root -> {
					Path innerJar = createJar( innerJarRoot -> {
						addMetaInfFile( innerJarRoot );
						addSimpleClass( innerJarRoot );
					} );
					Path innerJarInOuterJarAbsolute = root.resolve( innerJarInOuterJarRelativePathString );
					Files.createDirectories( innerJarInOuterJarAbsolute.getParent() );
					Files.copy( innerJar, innerJarInOuterJarAbsolute );
				}
		);
		try ( JarFile outerJar = new JarFile( outerJarPath.toFile() ) ) {
			URL innerJarURL =
					JarUrl.create( outerJarPath.toFile(), outerJar.getJarEntry( innerJarInOuterJarRelativePathString ) );
			try ( URLClassLoader isolatedClassLoader = createIsolatedClassLoader( innerJarURL ) ) {
				Class<?> classInIsolatedClassLoader = isolatedClassLoader.loadClass( SimpleClass.class.getName() );
				// Check preconditions: this is the situation that we want to test.
				URL location = classInIsolatedClassLoader.getProtectionDomain().getCodeSource().getLocation();
				assertThat( location.getProtocol() ).isEqualTo( "jar" );
				assertThat( location.toExternalForm() ).contains( innerJarInOuterJarRelativePathString );

				// Check that the JAR can be opened and that we can access other files within it
				try ( CodeSource codeSource = new CodeSource( location ) ) {
					try ( InputStream is = codeSource.readOrNull( META_INF_FILE_RELATIVE_PATH ) ) {
						assertThat( is ).hasBinaryContent( META_INF_FILE_CONTENT );
					}
					try ( InputStream is = codeSource.readOrNull( NON_EXISTING_FILE_RELATIVE_PATH ) ) {
						assertThat( is ).isNull();
					}
					if ( Runtime.version().feature() > 12 ) {
						// we are on JDK13+ and we should be able to read the nested JAR:
						Path nestedClassesPath = codeSource.classesPathOrFail();
						Path nestedJarRoot = nestedClassesPath.getRoot();

						try ( Stream<Path> files = Files.walk( nestedJarRoot ).filter( Files::isRegularFile ) ) {
							assertThat( files )
									.containsExactlyInAnyOrder(
											nestedJarRoot.resolve( META_INF_FILE_RELATIVE_PATH ),
											nestedClassesPath.resolve( SIMPLE_CLASS_RELATIVE_PATH )
									);
						}
					}
					else {
						// we are on JDK11/12 and inner JAR cannot be opened:
						assertThatThrownBy( codeSource::classesPathOrFail )
								.isInstanceOf( IOException.class )
								.hasMessageContainingAll(
										"Cannot open filesystem for code source at",
										location.toString(),
										"Cannot open a ZIP filesystem for code source at",
										location.toString(),
										"because the URI points to content inside a nested JAR.",
										"Run your application on JDK13+ to get nested JAR support",
										"or disable JAR scanning by setting a mapping configurer that calls .discoverAnnotatedTypesFromRootMappingAnnotations(false)",
										"See the reference documentation for information about mapping configurers."
								);
					}
				}
			}
		}
	}

	@Test
	void jar_jarScheme_specialCharacter() throws Exception {
		Path initialJarPath = createJar( root -> {
			addMetaInfFile( root );
			addSimpleClass( root );
		} );
		Path parentDirWithSpecialChar = Files.createTempDirectory( temporaryFolder, "hsearch" )
				.resolve( "parentnamewith%40special@char" );
		Files.createDirectories( parentDirWithSpecialChar );
		Path jarPath = Files.copy(
				initialJarPath,
				parentDirWithSpecialChar.resolve( "namewith%40special@char.jar" )
		);

		URI fileURL = jarPath.toUri();
		URL jarURL = new URI( "jar:" + fileURL + "!/" ).toURL();
		try ( URLClassLoader isolatedClassLoader = createIsolatedClassLoader( jarURL ) ) {
			Class<?> classInIsolatedClassLoader = isolatedClassLoader.loadClass( SimpleClass.class.getName() );

			URL location = classInIsolatedClassLoader.getProtectionDomain().getCodeSource().getLocation();
			// Check that the JAR can be opened and that we can access other files within it
			try ( CodeSource codeSource = new CodeSource( location ) ) {
				try ( InputStream is = codeSource.readOrNull( META_INF_FILE_RELATIVE_PATH ) ) {
					assertThat( is ).hasBinaryContent( META_INF_FILE_CONTENT );
				}
				try ( InputStream is = codeSource.readOrNull( NON_EXISTING_FILE_RELATIVE_PATH ) ) {
					assertThat( is ).isNull();
				}
				Path jarRoot = codeSource.classesPathOrFail();
				try ( Stream<Path> files = Files.walk( jarRoot ).filter( Files::isRegularFile ) ) {
					assertThat( files )
							.containsExactlyInAnyOrder(
									jarRoot.resolve( META_INF_FILE_RELATIVE_PATH ),
									jarRoot.resolve( SIMPLE_CLASS_RELATIVE_PATH )
							);
				}
			}
		}
	}

	@Test
	void jar_fileScheme_specialCharacter() throws Exception {
		Path initialJarPath = createJar( root -> {
			addMetaInfFile( root );
			addSimpleClass( root );
		} );
		Path parentDirWithSpecialChar = Files.createTempDirectory( temporaryFolder, "hsearch" )
				.resolve( "parentnamewith%40special@char" );
		Files.createDirectories( parentDirWithSpecialChar );
		Path jarPath = Files.copy(
				initialJarPath,
				parentDirWithSpecialChar.resolve( "namewith%40special@char.jar" )
		);

		try ( URLClassLoader isolatedClassLoader = createIsolatedClassLoader( jarPath.toUri().toURL() ) ) {
			Class<?> classInIsolatedClassLoader = isolatedClassLoader.loadClass( SimpleClass.class.getName() );

			URL location = classInIsolatedClassLoader.getProtectionDomain().getCodeSource().getLocation();
			// Check that the JAR can be opened and that we can access other files within it
			try ( CodeSource codeSource = new CodeSource( location ) ) {
				try ( InputStream is = codeSource.readOrNull( META_INF_FILE_RELATIVE_PATH ) ) {
					assertThat( is ).hasBinaryContent( META_INF_FILE_CONTENT );
				}
				try ( InputStream is = codeSource.readOrNull( NON_EXISTING_FILE_RELATIVE_PATH ) ) {
					assertThat( is ).isNull();
				}
				Path classesPath = codeSource.classesPathOrFail();
				try ( Stream<Path> files = Files.walk( classesPath ).filter( Files::isRegularFile ) ) {
					assertThat( files )
							.containsExactlyInAnyOrder(
									classesPath.resolve( META_INF_FILE_RELATIVE_PATH ),
									classesPath.resolve( SIMPLE_CLASS_RELATIVE_PATH )
							);
				}
			}
		}
	}

	@Test
	void directory_specialCharacter() throws Exception {
		Path initialDirPath = createDir( root -> {
			addMetaInfFile( root );
			addSimpleClass( root );
		} );
		Path parentDirWithSpecialChar = Files.createTempDirectory( temporaryFolder, "hsearch" )
				.resolve( "parentnamewith%40special@char" );
		Files.createDirectories( parentDirWithSpecialChar );
		Path dirPath = Files.move(
				initialDirPath,
				parentDirWithSpecialChar.resolve( "namewith%40special@char.jar" )
		);

		try ( URLClassLoader isolatedClassLoader = createIsolatedClassLoader( dirPath.toUri().toURL() ) ) {
			Class<?> classInIsolatedClassLoader = isolatedClassLoader.loadClass( SimpleClass.class.getName() );

			URL location = classInIsolatedClassLoader.getProtectionDomain().getCodeSource().getLocation();
			// Check that the JAR can be opened and that we can access other files within it
			try ( CodeSource codeSource = new CodeSource( location ) ) {
				try ( InputStream is = codeSource.readOrNull( META_INF_FILE_RELATIVE_PATH ) ) {
					assertThat( is ).hasBinaryContent( META_INF_FILE_CONTENT );
				}
				try ( InputStream is = codeSource.readOrNull( NON_EXISTING_FILE_RELATIVE_PATH ) ) {
					assertThat( is ).isNull();
				}
				Path classesPath = codeSource.classesPathOrFail();
				try ( Stream<Path> files = Files.walk( classesPath ).filter( Files::isRegularFile ) ) {
					assertThat( files )
							.containsExactlyInAnyOrder(
									classesPath.resolve( META_INF_FILE_RELATIVE_PATH ),
									classesPath.resolve( SIMPLE_CLASS_RELATIVE_PATH )
							);
				}
			}
		}
	}

	private Path createDir(ThrowingConsumer<Path, IOException> contributor) throws IOException {
		Path dirPath = Files.createTempDirectory( temporaryFolder, "hsearch" );
		contributor.accept( dirPath );
		return dirPath;
	}

	private Path createJar(ThrowingConsumer<Path, IOException> contributor) throws IOException {
		return createJar( null, contributor );
	}

	private Path createJar(Map<String, String> zipFsEnv, ThrowingConsumer<Path, IOException> contributor)
			throws IOException {
		return toJar( temporaryFolder, createDir( contributor ), zipFsEnv );
	}

	private void addMetaInfFile(Path root) throws IOException {
		Path file = root.resolve( "META-INF/someFile.txt" );
		Files.createDirectories( file.getParent() );
		try ( InputStream stream = new ByteArrayInputStream( META_INF_FILE_CONTENT ) ) {
			Files.copy( stream, file );
		}
	}

	private void addSimpleClass(Path classesDir) throws IOException {
		String classResourceName = SIMPLE_CLASS_RELATIVE_PATH;
		Path classFile = classesDir.resolve( Paths.get( classResourceName ) );
		Files.createDirectories( classFile.getParent() );
		try ( InputStream stream = getClass().getClassLoader().getResourceAsStream( classResourceName ) ) {
			Files.copy( stream, classFile );
		}
	}

	private static URLClassLoader createIsolatedClassLoader(URL jarURL) {
		return new URLClassLoader( new URL[] { jarURL }, null );
	}

}
