package com.badaklng.ses.user;

import com.badaklng.request.entity.AppEmpLkpView;
import com.badaklng.request.repository.AppEmpLkpViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserScopeServiceTest {

    @Mock
    private AppEmpLkpViewRepository empLkpViewRepository;

    @InjectMocks
    private UserScopeService userScopeService;

    private AppEmpLkpView createEmp(String emplid, String sectionId, String sectionDesc, String deptId, String deptDesc) {
        AppEmpLkpView emp = new AppEmpLkpView();
        emp.setEmplid(emplid);
        emp.setSectionId(sectionId);
        emp.setSectionDesc(sectionDesc);
        emp.setDeptId(deptId);
        emp.setDeptDesc(deptDesc);
        return emp;
    }

    @Test
    @DisplayName("User with non-null sectionId resolves SECTION scope with all section members")
    void testResolveScope_SectionScope() {
        AppEmpLkpView user = createEmp("313588", "SEC-IT-APP", "Aplikasi Bisnis", "DEPT-IT", "Information Technology");
        AppEmpLkpView peer1 = createEmp("313589", "SEC-IT-APP", "Aplikasi Bisnis", "DEPT-IT", "Information Technology");
        AppEmpLkpView peer2 = createEmp("313590", "SEC-IT-APP", "Aplikasi Bisnis", "DEPT-IT", "Information Technology");

        when(empLkpViewRepository.findByEmplid("313588")).thenReturn(Optional.of(user));
        when(empLkpViewRepository.findBySectionId("SEC-IT-APP")).thenReturn(Arrays.asList(user, peer1, peer2));

        UserScopeService.UserScopeResult result = userScopeService.resolveScope("313588");

        assertThat(result.getScopeType()).isEqualTo("SECTION");
        assertThat(result.getScopeLabel()).isEqualTo("Seksi Aplikasi Bisnis");
        assertThat(result.getUsernames()).containsExactlyInAnyOrder("313588", "313589", "313590");
        assertThat(result.isPersonal()).isFalse();
    }

    @Test
    @DisplayName("User with null sectionId but valid deptId resolves DEPARTMENT scope with all department members")
    void testResolveScope_DepartmentScope() {
        AppEmpLkpView manager = createEmp("310001", null, null, "DEPT-IT", "Information Technology");
        AppEmpLkpView member1 = createEmp("313588", "SEC-IT-APP", "Aplikasi Bisnis", "DEPT-IT", "Information Technology");
        AppEmpLkpView member2 = createEmp("313595", "SEC-IT-INFRA", "Infrastruktur", "DEPT-IT", "Information Technology");

        when(empLkpViewRepository.findByEmplid("310001")).thenReturn(Optional.of(manager));
        when(empLkpViewRepository.findByDeptId("DEPT-IT")).thenReturn(Arrays.asList(manager, member1, member2));

        UserScopeService.UserScopeResult result = userScopeService.resolveScope("310001");

        assertThat(result.getScopeType()).isEqualTo("DEPARTMENT");
        assertThat(result.getScopeLabel()).isEqualTo("Departemen Information Technology");
        assertThat(result.getUsernames()).containsExactlyInAnyOrder("310001", "313588", "313595");
        assertThat(result.isPersonal()).isFalse();
    }

    @Test
    @DisplayName("User with both sectionId and deptId null resolves PERSONAL scope")
    void testResolveScope_PersonalScopeWhenNoDeptOrSection() {
        AppEmpLkpView emp = createEmp("GUEST01", null, null, null, null);

        when(empLkpViewRepository.findByEmplid("GUEST01")).thenReturn(Optional.of(emp));

        UserScopeService.UserScopeResult result = userScopeService.resolveScope("GUEST01");

        assertThat(result.getScopeType()).isEqualTo("PERSONAL");
        assertThat(result.getScopeLabel()).isEqualTo("Personal");
        assertThat(result.getUsernames()).containsExactly("GUEST01");
        assertThat(result.isPersonal()).isTrue();
    }

    @Test
    @DisplayName("Unknown user not in employee view resolves fallback PERSONAL scope")
    void testResolveScope_UnknownUser() {
        when(empLkpViewRepository.findByEmplid("admin")).thenReturn(Optional.empty());

        UserScopeService.UserScopeResult result = userScopeService.resolveScope("admin");

        assertThat(result.getScopeType()).isEqualTo("PERSONAL");
        assertThat(result.getUsernames()).containsExactly("admin");
        assertThat(result.isPersonal()).isTrue();
    }
}
