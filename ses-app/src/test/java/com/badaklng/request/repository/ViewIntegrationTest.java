package com.badaklng.request.repository;

import com.badaklng.request.entity.AppEmpLkpView;
import com.badaklng.request.entity.ContractV;
import com.badaklng.request.entity.SesWorkerLookupView;
import com.badaklng.ses.SesApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SesApplication.class)
@ActiveProfiles("local")
class ViewIntegrationTest {

    @Autowired
    private ContractVRepository contractVRepository;

    @Autowired
    private AppEmpLkpViewRepository appEmpLkpViewRepository;

    @Autowired
    private AppEmpInfoViewRepository appEmpInfoViewRepository;

    @Autowired
    private SesWorkerLookupViewRepository sesWorkerLookupViewRepository;

    @Test
    void testContractVRepository() {
        assertNotNull(contractVRepository, "ContractVRepository bean should exist");
        long count = contractVRepository.count();
        System.out.println("ContractV total rows: " + count);
        assertTrue(count > 0, "CONTRACT_V view should have data");

        List<ContractV> search = contractVRepository.searchByTerm("CA-", PageRequest.of(0, 5));
        assertFalse(search.isEmpty(), "searchByTerm should return contracts");
        System.out.println("Found " + search.size() + " contracts matching 'CA-': " + search.get(0).getNoKontrak());
    }

    @Test
    void testAppEmpLkpViewRepository() {
        assertNotNull(appEmpLkpViewRepository, "AppEmpLkpViewRepository bean should exist");
        long count = appEmpLkpViewRepository.count();
        System.out.println("AppEmpLkpView total rows: " + count);
        assertTrue(count > 0, "APP_EMP_LKP_VIEW should have data");

        List<AppEmpLkpView> search = appEmpLkpViewRepository.searchByTerm("ADMIN", PageRequest.of(0, 5));
        assertFalse(search.isEmpty(), "searchByTerm should return employees matching 'ADMIN'");
        System.out.println("Found employee: " + search.get(0).getNameDisplay() + " (" + search.get(0).getEmplid() + ")");

        Optional<AppEmpLkpView> admin = appEmpLkpViewRepository.findByEmplid("ADMIN");
        assertTrue(admin.isPresent(), "ADMIN employee should be found");
        assertEquals("SYSTEM ADMINISTRATOR", admin.get().getNameDisplay());
    }

    @Test
    void testAppEmpInfoViewRepository() {
        assertNotNull(appEmpInfoViewRepository, "AppEmpInfoViewRepository bean should exist");
        long count = appEmpInfoViewRepository.count();
        assertTrue(count >= 0, "APP_EMP_INFO_VIEW count should be >= 0");
    }

    @Test
    void testActiveMitraComesFromAppPerson() {
        List<SesWorkerLookupView> workers = sesWorkerLookupViewRepository
                .searchByBadgePrefix("309202", PageRequest.of(0, 5));

        assertFalse(workers.isEmpty(), "Active APP_PERSON Mitra worker should be searchable");
        SesWorkerLookupView worker = workers.get(0);
        assertEquals("309202", worker.getBadge());
        assertEquals("M", worker.getGender(), "APP_PERSON gender L should map to M");
    }
}
