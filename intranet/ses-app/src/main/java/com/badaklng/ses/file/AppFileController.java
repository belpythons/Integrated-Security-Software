package com.badaklng.ses.file;

import com.badaklng.platform.system.entity.AppUser;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Streams uploaded files back out. Authorisation is mandatory: a bare
 * {@code /file/{uuid}} endpoint would otherwise expose every uploaded document in
 * the system to any logged-in user. Access is resolved via the {@link FileOwnershipResolver}
 * SPI, collected loosely through {@link ObjectProvider} so this controller has zero
 * compile-time dependency on any business module. Deny by default: if no registered
 * resolver claims the file's refId, the request gets a 403.
 */
@Controller
public class AppFileController {

    private final AppFileService fileService;
    private final ObjectProvider<FileOwnershipResolver> resolvers;
    private final AppUserRepository appUserRepository;

    public AppFileController(AppFileService fileService,
                             ObjectProvider<FileOwnershipResolver> resolvers,
                             AppUserRepository appUserRepository) {
        this.fileService = fileService;
        this.resolvers = resolvers;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping("/file/{fileId}")
    public void download(@PathVariable String fileId, HttpServletResponse response) throws IOException {
        SesAppFile file = fileService.get(fileId);

        String username = SecurityUtils.getCurrentUsername();
        String userId = appUserRepository.findByUsername(username).map(AppUser::getUserId).orElse(null);

        boolean allowed = false;
        for (FileOwnershipResolver resolver : resolvers) {
            if (resolver.supports(file.getRefId())) {
                // First resolver that CLAIMS the refId decides — do not fall through to another.
                allowed = resolver.canAccess(file.getRefId(), username, userId);
                break;
            }
        }

        if (!allowed) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String type = file.getFileType();
        boolean previewable = type != null && (type.startsWith("image/") || type.equals("application/pdf"));
        String disposition = previewable ? "inline" : "attachment";
        response.setContentType(type);
        response.setHeader("Content-Disposition", disposition + "; filename=\"" + file.getFileName() + "\"");
        response.setContentLengthLong(file.getFileSize());
        response.getOutputStream().write(file.getFileData());
        response.getOutputStream().flush();
    }
}
