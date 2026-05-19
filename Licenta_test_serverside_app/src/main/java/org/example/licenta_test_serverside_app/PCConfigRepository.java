package org.example.licenta_test_serverside_app;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
@Transactional
public class PCConfigRepository {

    @PersistenceContext
    private EntityManager em;

    public List<PCConfig> findAll() {
        return em.createQuery("SELECT p FROM PCConfig p", PCConfig.class).getResultList();
    }

    public PCConfig findById(Long id) {
        return em.find(PCConfig.class, id);
    }

    public void create(PCConfig config) {
        em.persist(config);
    }

    public PCConfig update(PCConfig config) {
        return em.merge(config);
    }

    public void delete(Long id) {
        PCConfig config = findById(id);
        if (config != null) {
            em.remove(config);
        }
    }
}