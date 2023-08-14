package run.ikaros.plugin.bgmtv.constants;

import java.io.File;
import java.util.Map;

public interface BgmTvConst {
    String DATE_PATTERN = "YYYY-MM-DD";
    String SUBJECT_PREFIX = "https://bgm.tv/subject" + File.separator;
    String HOME_PAGE = "https://ikaros.run";
    String REPO_GITHUB_NAME = "ikaros-dev/ikaros";
    String REST_TEMPLATE_USER_AGENT = REPO_GITHUB_NAME + " (" + HOME_PAGE + ")";
    String TOKEN_PREFIX = "Bearer ";
}
