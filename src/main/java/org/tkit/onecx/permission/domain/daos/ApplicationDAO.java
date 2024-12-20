package org.tkit.onecx.permission.domain.daos;

import static org.tkit.quarkus.jpa.utils.QueryCriteriaUtil.addSearchStringPredicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;

import org.tkit.onecx.permission.domain.criteria.ApplicationSearchCriteria;
import org.tkit.onecx.permission.domain.models.*;
import org.tkit.quarkus.jpa.daos.AbstractDAO;
import org.tkit.quarkus.jpa.daos.Page;
import org.tkit.quarkus.jpa.daos.PageResult;
import org.tkit.quarkus.jpa.exceptions.DAOException;
import org.tkit.quarkus.jpa.models.AbstractTraceableEntity_;

@ApplicationScoped
public class ApplicationDAO extends AbstractDAO<Application> {

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public PageResult<Application> findByCriteria(ApplicationSearchCriteria criteria) {
        try {
            var cb = this.getEntityManager().getCriteriaBuilder();
            var cq = cb.createQuery(Application.class);
            var root = cq.from(Application.class);

            List<Predicate> predicates = new ArrayList<>();
            addSearchStringPredicate(predicates, cb, root.get(Application_.appId), criteria.getAppId());
            addSearchStringPredicate(predicates, cb, root.get(Application_.name), criteria.getName());
            addSearchStringPredicate(predicates, cb, root.get(Application_.productName), criteria.getProductName());
            if (!predicates.isEmpty()) {
                cq.where(predicates.toArray(new Predicate[] {}));
            }

            cq.orderBy(cb.desc(root.get(AbstractTraceableEntity_.CREATION_DATE)));
            return createPageQuery(cq, Page.of(criteria.getPageNumber(), criteria.getPageSize())).getPageResult();
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_FIND_APPLICATIONS_BY_CRITERIA, ex);
        }
    }

    public Application loadByName(String appName) {
        try {
            var cb = this.getEntityManager().getCriteriaBuilder();
            var cq = cb.createQuery(Application.class);
            var root = cq.from(Application.class);
            cq.where(
                    cb.equal(root.get(Application_.NAME), appName));
            return this.getEntityManager().createQuery(cq).getSingleResult();
        } catch (NoResultException ne) {
            return null;
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_LOAD_BY_APP_NAME, ex);
        }
    }

    public Application loadByAppId(String productName, String appId) {
        try {
            var cb = this.getEntityManager().getCriteriaBuilder();
            var cq = cb.createQuery(Application.class);
            var root = cq.from(Application.class);
            cq.where(
                    cb.equal(root.get(Application_.PRODUCT_NAME), productName),
                    cb.equal(root.get(Application_.APP_ID), appId));
            return this.getEntityManager().createQuery(cq).getSingleResult();
        } catch (NoResultException ne) {
            return null;
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_LOAD_BY_APP_ID, ex);
        }
    }

    public List<ApplicationRef> findByProductNames(Set<String> productNames) {
        try {
            var cb = this.getEntityManager().getCriteriaBuilder();
            var cq = cb.createQuery(ApplicationRef.class);
            var root = cq.from(Application.class);
            cq.select(cb.construct(ApplicationRef.class, root.get(Application_.PRODUCT_NAME), root.get(Application_.APP_ID)));
            cq.where(root.get(Application_.PRODUCT_NAME).in(productNames));
            return this.getEntityManager().createQuery(cq).getResultList();
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_FIND_APPLICATIONS_BY_PRODUCT_NAMES, ex);
        }
    }

    public enum ErrorKeys {

        ERROR_FIND_APPLICATIONS_BY_PRODUCT_NAMES,

        ERROR_FIND_APPLICATIONS_BY_CRITERIA,
        ERROR_LOAD_BY_APP_ID,
        ERROR_LOAD_BY_APP_NAME;
    }
}
