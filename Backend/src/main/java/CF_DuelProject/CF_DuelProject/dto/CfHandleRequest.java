package CF_DuelProject.CF_DuelProject.dto;

import lombok.Data;

@Data
public class CfHandleRequest {
    private String cfHandle;
    private String handle;
    private String codeforcesHandle;

    public String getCfHandle() {
        if (this.cfHandle != null && !this.cfHandle.isBlank()) {
            return this.cfHandle;
        }
        if (this.handle != null && !this.handle.isBlank()) {
            return this.handle;
        }
        return this.codeforcesHandle;
    }
}

