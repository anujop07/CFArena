package CF_DuelProject.CF_DuelProject.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "users")
public class User {
  
    @Id
    private String id;

    private String name;
    private String email;
    private String password;

    private String provider; 

    private String cfHandle;

    // ✅ Role-based access control — set manually in DB for admin users
    private boolean admin = false;
}
