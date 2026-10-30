package dev.overgrown.birdsong.client.speech;

import dev.overgrown.birdsong.Birdsong;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VoskRuntime {
    private static final String[] LIBS = {"jna.jar", "vosk.jar"};
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private static boolean attempted;
    private static @Nullable Bridge bridge;
    private static @Nullable String failure;

    private VoskRuntime() {}

    public static synchronized boolean available() {
        return bridge() != null;
    }

    public static synchronized @Nullable String failure() {
        bridge();
        return failure;
    }

    public static SpeechEngine open(String modelPath, float sampleRate) throws Exception {
        Bridge b = bridge();
        if (b == null) throw new IllegalStateException("the speech engine could not start: " + failure);
        try {
            return new Session(b, modelPath, sampleRate);
        } catch (Exception | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static synchronized @Nullable Bridge bridge() {
        if (attempted) return bridge;
        attempted = true;
        try {
            bridge = Bridge.create(loader());
        } catch (Throwable t) {
            failure = describe(t);
            Birdsong.LOGGER.error("[Birdsong] Could not start the speech engine", t);
        }
        return bridge;
    }

    private static ClassLoader loader() throws Exception {
        Path dir = FMLPaths.GAMEDIR.get().resolve("birdsong").resolve("runtime");
        Files.createDirectories(dir);
        URL[] urls = new URL[LIBS.length];
        for (int i = 0; i < LIBS.length; i++) urls[i] = extract(dir, LIBS[i]).toUri().toURL();
        return new IsolatedLoader(urls, VoskRuntime.class.getClassLoader());
    }

    private static Path extract(Path dir, String name) throws Exception {
        Path target = dir.resolve(name);
        URL source = VoskRuntime.class.getResource("/birdsong/runtime/" + name);
        if (source == null) {
            if (Files.isRegularFile(target)) return target;
            throw new IllegalStateException("missing bundled library birdsong/runtime/" + name);
        }
        long expected = source.openConnection().getContentLengthLong();
        if (expected >= 0 && Files.isRegularFile(target) && Files.size(target) == expected) return target;
        Path tmp = dir.resolve(name + ".tmp");
        try (InputStream in = source.openStream(); OutputStream out = Files.newOutputStream(tmp)) {
            in.transferTo(out);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        Birdsong.LOGGER.info("[Birdsong] Unpacked speech library {} ({} bytes).", name, Files.size(target));
        return target;
    }

    private static final class IsolatedLoader extends URLClassLoader {
        static {
            registerAsParallelCapable();
        }

        IsolatedLoader(URL[] urls, ClassLoader parent) {
            super("birdsong-vosk", urls, parent);
        }

        private static boolean owned(String name) {
            return name.startsWith("com.sun.jna.") || name.startsWith("org.vosk.");
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (owned(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }

        @Override
        public URL getResource(String name) {
            URL own = findResource(name);
            return own != null ? own : super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            List<URL> ordered = new ArrayList<>(4);
            for (Enumeration<URL> own = findResources(name); own.hasMoreElements(); ) ordered.add(own.nextElement());
            for (Enumeration<URL> inherited = super.getResources(name); inherited.hasMoreElements(); ) {
                URL url = inherited.nextElement();
                if (!ordered.contains(url)) ordered.add(url);
            }
            return Collections.enumeration(ordered);
        }
    }

    private record Bridge(MethodHandle newModel, MethodHandle newRecognizer, MethodHandle reset,
                          MethodHandle acceptBytes, MethodHandle acceptShorts, MethodHandle result, MethodHandle partial,
                          MethodHandle finalResult, MethodHandle closeModel, MethodHandle closeRecognizer) {
        private static final MethodType STRING_RESULT = MethodType.methodType(String.class, Object.class);
        private static final MethodType VOID_CALL = MethodType.methodType(void.class, Object.class);

        static Bridge create(ClassLoader cl) throws Exception {
            Class<?> model = prepare(cl);
            Class<?> recognizer = Class.forName("org.vosk.Recognizer", true, cl);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            return new Bridge(
                lookup.unreflectConstructor(model.getConstructor(String.class)).asType(MethodType.methodType(Object.class, String.class)),
                lookup.unreflectConstructor(recognizer.getConstructor(model, float.class))
                    .asType(MethodType.methodType(Object.class, Object.class, float.class)),
                lookup.unreflect(recognizer.getMethod("reset")).asType(VOID_CALL),
                lookup.unreflect(recognizer.getMethod("acceptWaveForm", byte[].class, int.class))
                    .asType(MethodType.methodType(boolean.class, Object.class, byte[].class, int.class)),
                lookup.unreflect(recognizer.getMethod("acceptWaveForm", short[].class, int.class))
                    .asType(MethodType.methodType(boolean.class, Object.class, short[].class, int.class)),
                lookup.unreflect(recognizer.getMethod("getResult")).asType(STRING_RESULT),
                lookup.unreflect(recognizer.getMethod("getPartialResult")).asType(STRING_RESULT),
                lookup.unreflect(recognizer.getMethod("getFinalResult")).asType(STRING_RESULT),
                lookup.unreflect(model.getMethod("close")).asType(VOID_CALL),
                lookup.unreflect(recognizer.getMethod("close")).asType(VOID_CALL));
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Class<?> prepare(ClassLoader cl) throws Exception {
            Class<?> nativeClass = Class.forName("com.sun.jna.Native", true, cl);
            Package pkg = nativeClass.getPackage();
            String version = pkg == null ? null : pkg.getImplementationVersion();
            Birdsong.LOGGER.info("[Birdsong] Speech engine: JNA {} in {}, jnidispatch at {}.",
                version == null ? "unknown" : version, nativeClass.getClassLoader().getName(), System.getProperty("jnidispatch.path"));
            Class<?> level = Class.forName("org.vosk.LogLevel", true, cl);
            Class.forName("org.vosk.LibVosk", true, cl).getMethod("setLogLevel", level)
                .invoke(null, Enum.valueOf((Class) level, "WARNINGS"));
            return Class.forName("org.vosk.Model", true, cl);
        }
    }

    private static String describe(Throwable t) {
        Throwable root = t;
        while (root.getMessage() == null && root.getCause() != null) root = root.getCause();
        String message = root.getMessage();
        String text = root.getClass().getSimpleName();
        return message == null ? text : text + ": " + message.replace('\n', ' ').trim();
    }

    private static void logVoskLibrary(ClassLoader cl) {
        try {
            Object library = Class.forName("com.sun.jna.NativeLibrary", true, cl)
                .getMethod("getInstance", String.class, ClassLoader.class)
                .invoke(null, System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "libvosk" : "vosk", cl);
            Object file = library.getClass().getMethod("getFile").invoke(library);
            Birdsong.LOGGER.info("[Birdsong] Speech engine: libvosk loaded from {}.", file);
        } catch (Throwable t) {
            Birdsong.LOGGER.debug("[Birdsong] Could not report the libvosk path", t);
        }
    }

    private static final class Session implements SpeechEngine {
        private final Bridge bridge;
        private final Object model;
        private final Object recognizer;

        Session(Bridge bridge, String modelPath, float sampleRate) throws Throwable {
            this.bridge = bridge;
            this.model = (Object) bridge.newModel().invokeExact(modelPath);
            if (REPORTED.compareAndSet(false, true)) logVoskLibrary(model.getClass().getClassLoader());
            Object created;
            try {
                created = (Object) bridge.newRecognizer().invokeExact(model, sampleRate);
            } catch (Throwable t) {
                bridge.closeModel().invokeExact(model);
                throw t;
            }
            this.recognizer = created;
        }

        private static RuntimeException rethrow(Throwable t) {
            if (t instanceof RuntimeException runtime) return runtime;
            if (t instanceof Error error) throw error;
            return new IllegalStateException(t);
        }

        @Override
        public boolean accept(short[] samples, int length) {
            try {
                return (boolean) bridge.acceptShorts().invokeExact(recognizer, samples, length);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        @Override
        public boolean accept(byte[] data, int length) {
            try {
                return (boolean) bridge.acceptBytes().invokeExact(recognizer, data, length);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        @Override
        public String result() {
            try {
                return (String) bridge.result().invokeExact(recognizer);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        @Override
        public String partial() {
            try {
                return (String) bridge.partial().invokeExact(recognizer);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        @Override
        public String finalResult() {
            try {
                return (String) bridge.finalResult().invokeExact(recognizer);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        @Override
        public void reset() {
            try {
                bridge.reset().invokeExact(recognizer);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        @Override
        public void close() {
            try {
                bridge.closeRecognizer().invokeExact(recognizer);
            } catch (Throwable ignored) {
            }
            try {
                bridge.closeModel().invokeExact(model);
            } catch (Throwable ignored) {
            }
        }
    }
}
