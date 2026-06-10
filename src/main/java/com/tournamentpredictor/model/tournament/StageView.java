package com.tournamentpredictor.model.tournament;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class StageView {
        private final String label;
        private final String description;
        private final StageStatus status;
        private final boolean canRun;
        private final String runMode;
        private final String runLabel;
        private final String runButtonClass;
        private final boolean canEdit;
        private final String editUrl;
        private final boolean canView;
        private final String viewUrl;
        private final boolean canView2;
        private final String viewUrl2;
        private final String viewLabel2;
        private final boolean canLoadActual;
        private final String loadActualUrl;
        private final boolean canReset;
        private final String resetUrl;

        public StageView(String label, String description, StageStatus status, boolean canRun, String runMode, boolean canEdit, String editUrl, boolean canView, String viewUrl, boolean canReset, String resetUrl) {
            this(label, description, status, canRun, runMode, null, null, canEdit, editUrl, canView, viewUrl, false, null, null, false, null, canReset, resetUrl);
        }

        public StageView(String label, String description, StageStatus status, boolean canRun, String runMode, boolean canEdit, String editUrl, boolean canView, String viewUrl, boolean canView2, String viewUrl2, String viewLabel2, boolean canReset, String resetUrl) {
            this(label, description, status, canRun, runMode, null, null, canEdit, editUrl, canView, viewUrl, canView2, viewUrl2, viewLabel2, false, null, canReset, resetUrl);
        }

        public StageView(String label, String description, StageStatus status, boolean canRun, String runMode,
                         String runLabel, String runButtonClass, boolean canEdit, String editUrl,
                         boolean canView, String viewUrl, boolean canView2, String viewUrl2,
                         String viewLabel2, boolean canLoadActual, String loadActualUrl,
                         boolean canReset, String resetUrl) {
            this.label = label;
            this.description = description;
            this.status = status;
            this.canRun = canRun;
            this.runMode = runMode;
            this.runLabel = runLabel != null ? runLabel : "Review";
            this.runButtonClass = runButtonClass != null ? runButtonClass : "btn-primary";
            this.canEdit = canEdit;
            this.editUrl = editUrl;
            this.canView = canView;
            this.viewUrl = viewUrl;
            this.canView2 = canView2;
            this.viewUrl2 = viewUrl2;
            this.viewLabel2 = viewLabel2 != null ? viewLabel2 : "View";
            this.canLoadActual = canLoadActual;
            this.loadActualUrl = loadActualUrl;
            this.canReset = canReset;
            this.resetUrl = resetUrl;
        }

        public String getLabel() { return label; }
        public String getDescription() { return description; }
        public StageStatus getStatus() { return status; }
        public boolean isCanRun() { return canRun; }
        public String getRunMode() { return runMode; }
        public String getRunLabel() { return runLabel; }
        public String getRunButtonClass() { return runButtonClass; }
        public boolean isCanEdit() { return canEdit; }
        public String getEditUrl() { return editUrl; }
        public boolean isCanView() { return canView; }
        public String getViewUrl() { return viewUrl; }
        public boolean isCanView2() { return canView2; }
        public String getViewUrl2() { return viewUrl2; }
        public String getViewLabel2() { return viewLabel2; }
        public boolean isCanLoadActual() { return canLoadActual; }
        public String getLoadActualUrl() { return loadActualUrl; }
        public boolean isCanReset() { return canReset; }
        public String getResetUrl() { return resetUrl; }
    }
