/*
 * eGov suite of products aim to improve the internal efficiency,transparency,
 *    accountability and the service delivery of the government  organizations.
 *
 *     Copyright (C) <2015>  eGovernments Foundation
 *
 *     The updated version of eGov suite of products as by eGovernments Foundation
 *     is available at http://www.egovernments.org
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see http://www.gnu.org/licenses/ or
 *     http://www.gnu.org/licenses/gpl.html .
 *
 *     In addition to the terms of the GPL license to be adhered to in using this
 *     program, the following additional terms are to be complied with:
 *
 *         1) All versions of this program, verbatim or modified must carry this
 *            Legal Notice.
 *
 *         2) Any misrepresentation of the origin of the material is prohibited. It
 *            is required that all modified versions of this material be marked in
 *            reasonable ways as different from the original version.
 *
 *         3) This license does not grant any rights to any user of the program
 *            with regards to rights under trademark law for use of the trade names
 *            or trademarks of eGovernments Foundation.
 *
 *   In case of any queries, you can reach eGovernments Foundation at contact@egovernments.org.
 */
package org.egov.lcms.transactions.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.egov.infra.persistence.entity.AbstractAuditable;
import org.egov.infra.persistence.validator.annotation.OptionalPattern;
import org.egov.infra.persistence.validator.annotation.Required;
import org.egov.infra.persistence.validator.annotation.ValidateDate;
import org.egov.infra.utils.DateUtils;
import org.egov.infra.validation.exception.ValidationError;
import org.egov.lcms.utils.LcmsConstants;
import org.hibernate.validator.constraints.Length;

/**
 * Contempt entity.
 *
 * @author MyEclipse Persistence Tools
 */
@Entity
@Table(name = "EGLC_CONTEMPT")
@SequenceGenerator(name = Contempt.SEQ_EGLC_CONTEMPT, sequenceName = Contempt.SEQ_EGLC_CONTEMPT, allocationSize = 1)
public class Contempt extends AbstractAuditable {
    private static final long serialVersionUID = 1517694643078084884L;
    public static final String SEQ_EGLC_CONTEMPT = "SEQ_EGLC_CONTEMPT";

    // Fields
    @Id
    @GeneratedValue(generator = SEQ_EGLC_CONTEMPT, strategy = GenerationType.SEQUENCE)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @JoinColumn(name = "JUDGMENTIMPL")
    private Judgmentimpl judgmentimpl;
    @Required(message = "canumber.null")
    @Length(max = 50, message = "canumber.length")
    @OptionalPattern(regex = LcmsConstants.alphaNumeric, message = "canumber.alpha")
    private String caNumber;
    @Required(message = "receivingdate.null")
    @ValidateDate(allowPast = true, dateFormat = LcmsConstants.DATE_FORMAT, message = "invalid.contempt.date")
    private Date receivingdate;
    private boolean iscommapprRequired = false;
    private Date commappDate;

    public Judgmentimpl getJudgmentimpl() {
        return judgmentimpl;
    }

    public void setJudgmentimpl(final Judgmentimpl judgmentimpl) {
        this.judgmentimpl = judgmentimpl;
    }

    public String getCanumber() {
        return caNumber;
    }

    public void setCanumber(final String canumber) {
        caNumber = canumber;
    }

    public Date getReceivingdate() {
        return receivingdate;
    }

    public void setReceivingdate(final Date receivingdate) {
        this.receivingdate = receivingdate;
    }

    public boolean getIscommapprRequired() {
        return iscommapprRequired;
    }

    public void setIscommapprRequired(final boolean iscommapprRequired) {
        this.iscommapprRequired = iscommapprRequired;
    }

    public Date getCommappDate() {
        return commappDate;
    }

    public void setCommappDate(final Date commappDate) {
        this.commappDate = commappDate;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(final Long id) {
        this.id = id;
    }

    public List<ValidationError> validate() {
        final List<ValidationError> errors = new ArrayList<ValidationError>();
        if (getReceivingdate() != null) {
            if (!DateUtils.compareDates(getReceivingdate(), getJudgmentimpl().getJudgment().getOrderDate()))
                errors.add(new ValidationError("receivingDate", "receivingDate.less.orderDate"));
            if (!DateUtils.compareDates(getCommappDate(), getReceivingdate()))
                errors.add(new ValidationError("receivingDate", "commappDate.greaterThan.receivingDate"));
        }
        return errors;
    }

}