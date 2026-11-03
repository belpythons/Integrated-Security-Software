package com.badaklng.ses.user;

import com.badaklng.request.entity.AppEmpLkpView;
import com.badaklng.request.repository.AppEmpLkpViewRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves data access scopes for a user based on employee master hierarchy:
 * 1. If user has a non-blank sectionId: scope includes all employees in that SECTION.
 * 2. If sectionId is blank but deptId is present: scope includes all employees in that DEPARTMENT.
 * 3. Otherwise (or non-employee / system account): PERSONAL scope only (the username itself).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserScopeService {

    private final AppEmpLkpViewRepository empLkpViewRepository;

    @Getter
    public static class UserScopeResult {
        private final String username;
        private final Set<String> usernames;
        private final String scopeType; // "SECTION", "DEPARTMENT", "PERSONAL"
        private final String scopeName; // e.g., "Aplikasi Bisnis", "Information Technology", or username
        private final String scopeLabel; // User-facing description

        public UserScopeResult(String username, Set<String> usernames, String scopeType, String scopeName, String scopeLabel) {
            this.username = username;
            this.usernames = usernames != null ? usernames : Collections.singleton(username);
            this.scopeType = scopeType;
            this.scopeName = scopeName;
            this.scopeLabel = scopeLabel;
        }

        public boolean isPersonal() {
            return "PERSONAL".equals(scopeType);
        }
    }

    @Transactional(readOnly = true)
    public UserScopeResult resolveScope(String username) {
        if (username == null || username.trim().isEmpty()) {
            return new UserScopeResult("", Collections.emptySet(), "PERSONAL", "Personal", "Personal");
        }

        String trimmedUser = username.trim();
        Optional<AppEmpLkpView> empOpt = empLkpViewRepository.findByEmplid(trimmedUser);

        if (!empOpt.isPresent()) {
            return new UserScopeResult(trimmedUser, Collections.singleton(trimmedUser), "PERSONAL", trimmedUser, "Personal");
        }

        AppEmpLkpView emp = empOpt.get();
        String sectionId = emp.getSectionId();
        String sectionDesc = emp.getSectionDesc();
        String deptId = emp.getDeptId();
        String deptDesc = emp.getDeptDesc();

        if (sectionId != null && !sectionId.trim().isEmpty()) {
            List<AppEmpLkpView> members = empLkpViewRepository.findBySectionId(sectionId.trim());
            Set<String> set = new HashSet<>();
            set.add(trimmedUser);
            for (AppEmpLkpView m : members) {
                if (m.getEmplid() != null && !m.getEmplid().trim().isEmpty()) {
                    set.add(m.getEmplid().trim());
                }
            }
            String sName = (sectionDesc != null && !sectionDesc.trim().isEmpty()) ? sectionDesc.trim() : sectionId.trim();
            return new UserScopeResult(trimmedUser, set, "SECTION", sName, "Seksi " + sName);
        }

        if (deptId != null && !deptId.trim().isEmpty()) {
            List<AppEmpLkpView> members = empLkpViewRepository.findByDeptId(deptId.trim());
            Set<String> set = new HashSet<>();
            set.add(trimmedUser);
            for (AppEmpLkpView m : members) {
                if (m.getEmplid() != null && !m.getEmplid().trim().isEmpty()) {
                    set.add(m.getEmplid().trim());
                }
            }
            String dName = (deptDesc != null && !deptDesc.trim().isEmpty()) ? deptDesc.trim() : deptId.trim();
            return new UserScopeResult(trimmedUser, set, "DEPARTMENT", dName, "Departemen " + dName);
        }

        return new UserScopeResult(trimmedUser, Collections.singleton(trimmedUser), "PERSONAL", trimmedUser, "Personal");
    }
}
