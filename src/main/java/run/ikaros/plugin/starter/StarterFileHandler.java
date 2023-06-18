package run.ikaros.plugin.starter;

import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import reactor.core.publisher.Mono;
import run.ikaros.api.core.file.File;
import run.ikaros.api.core.file.FileConst;
import run.ikaros.api.core.file.FileHandler;
import run.ikaros.api.core.file.FilePolicy;
import run.ikaros.api.custom.ReactiveCustomClient;


@Slf4j
@Extension
public class StarterFileHandler implements FileHandler {

    private final ReactiveCustomClient reactiveCustomClient;

    public StarterFileHandler(ReactiveCustomClient reactiveCustomClient) {
        this.reactiveCustomClient = reactiveCustomClient;
    }

    @Override
    public String policy() {
        return FileConst.POLICY_LOCAL;
    }

    @Override
    public Mono<File> upload(UploadContext uploadContext) {
        log.info("upload");
        return null;
    }

    @Override
    public Mono<File> delete(File file) {
        log.info("delete");
        return null;
    }


}
