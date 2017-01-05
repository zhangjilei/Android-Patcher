package cn.jesse.patcher.build.gradle.transform

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.google.common.collect.ImmutableSet
import com.google.common.io.Files
import groovy.io.FileType
import org.gradle.api.Project


/**
 * Created by jesse on 24/12/2016.
 */

public class AuxiliaryInjectTransform extends Transform {
    private static final String TRANSFORM_NAME = 'AuxiliaryInject'

    private final Project project

    private boolean isEnabled = false

    def applicationVariants

    public AuxiliaryInjectTransform(Project project) {
        this.project = project

        project.afterEvaluate {
            this.isEnabled = project.patcher.dex.usePreGeneratedPatchDex

            this.applicationVariants = project.android.applicationVariants
        }
    }

    /**
     * Returns the unique name of the transform.
     *
     * <p/>
     * This is associated with the type of work that the transform does. It does not have to be
     * unique per variant.
     */
    @Override
    String getName() {
        return TRANSFORM_NAME
    }

    /**
     * Returns the type(s) of data that is consumed by the Transform. This may be more than
     * one type.
     *
     * <strong>This must be of type {@link QualifiedContent.DefaultContentType}</strong>
     */
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES)
    }

    /**
     * Returns the scope(s) of the Transform. This indicates which scopes the transform consumes.
     */
    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return ImmutableSet.of(
                QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.SUB_PROJECTS,
                QualifiedContent.Scope.PROJECT_LOCAL_DEPS,
                QualifiedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES
        )
    }

    /**
     * Returns whether the Transform can perform incremental work.
     *
     * <p/>
     * If it does, then the TransformInput may contain a list of changed/removed/added files, unless
     * something else triggers a non incremental run.
     */
    @Override
    boolean isIncremental() {
        return true
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        printMsgLog("Inject is %b , incremental is %b", isEnabled, transformInvocation.incremental)

        // 收集当前Transform的输入数据
        def dirInputs = new HashSet<>()
        def jarInputs = new HashSet<>()

        transformInvocation.inputs.each { input ->
            input.directoryInputs.each { dirInput ->
                dirInputs.add(dirInput)
            }
            input.jarInputs.each { jarInput ->
                jarInputs.add(jarInput)
            }
        }

        if (dirInputs.isEmpty() && jarInputs.isEmpty())
            return;

        // 创建当前Transform的output路径
        File dirOutput = transformInvocation.outputProvider.getContentLocation(
                "classes", getOutputTypes(), getScopes(), Format.DIRECTORY)
        if (!dirOutput.exists()) {
            dirOutput.mkdirs()
        }

        printMsgLog("Outputs " + dirOutput.absolutePath)

        // 遍历dir,直接使用traverse方法获取到路径下的文件即class文件,做插桩然后copy到对应的输出路径下
        if (!dirInputs.isEmpty()) {
            dirInputs.each { dirInput ->
                dirInput.file.traverse(type: FileType.FILES) { fileInput ->
                    File fileOutput = new File(fileInput.getAbsolutePath().replace(dirInput.file.getAbsolutePath(), dirOutput.getAbsolutePath()))
                    if (!fileOutput.exists()) {
                        fileOutput.getParentFile().mkdirs()
                    }
                    printMsgLog('Copying class %s to output', fileInput.absolutePath, fileOutput.absolutePath)
                    Files.copy(fileInput, fileOutput)
                }
            }
        }

        // 遍历jar文件, 插桩处理,然后copy到对应的输出路径
        if (!jarInputs.isEmpty()) {

            jarInputs.each { jarInput ->
                File jarInputFile = jarInput.file
                File jarOutputFile = transformInvocation.outputProvider.getContentLocation(
                        jarInputFile.absolutePath, getOutputTypes(), getScopes(), Format.JAR)
                if (!jarOutputFile.exists()) {
                    jarOutputFile.getParentFile().mkdirs()
                }
                printMsgLog('Copying Jar %s output', jarInputFile.absolutePath, jarOutputFile.absolutePath)
                Files.copy(jarInputFile, jarOutputFile)
            }
        }
    }

    private void printMsgLog(String fmt, Object... vals) {
        final String title = TRANSFORM_NAME.capitalize()
        this.project.logger.lifecycle("[{}] {}", title,
                (vals == null || vals.length == 0 ? fmt : String.format(fmt, vals)))
    }

    private void printWarnLog(String fmt, Object... vals) {
        final String title = TRANSFORM_NAME.capitalize()
        this.project.logger.error("[{}] {}", title,
                (vals == null || vals.length == 0 ? fmt : String.format(fmt, vals)))
    }
}
