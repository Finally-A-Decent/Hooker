package info.preva1l.hooker.annotation.processor;

import com.google.auto.service.AutoService;
import info.preva1l.hooker.HookOrder;
import info.preva1l.hooker.annotation.Hook;
import info.preva1l.hooker.annotation.Reloadable;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

/**
 * Created on 10/03/2025
 *
 * @author Preva1l
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({
        "info.preva1l.hooker.annotation.Hook",
        "info.preva1l.hooker.annotation.Reloadable"
})
@ApiStatus.Internal
public class IncompatibilityProcessor extends AbstractProcessor {
    /**
     * <b>Do not use.</b>
     */
    public IncompatibilityProcessor() {
        super();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Reloadable.class)) {
            Hook hookAnnotation = element.getAnnotation(Hook.class);
            if (hookAnnotation == null) continue;
            if (hookAnnotation.order() != HookOrder.LOAD) continue;
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Hooks that are set to load during the onLoad event cannot be annotated with @Reloadable!",
                    element
            );
        }
        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        SourceVersion latest = SourceVersion.latest();
        if (latest.compareTo(SourceVersion.RELEASE_17) >= 0) {
            return latest;
        }
        return SourceVersion.RELEASE_17;
    }
}