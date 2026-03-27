package ca.digitalcave.buddi.live.model;

import java.util.Date;

public class Source {
	private Integer id;
	private int userId;
	private String uuid;
	private String name;
	private boolean deleted;
	private Date created;
	private Date modified;
	private String type;

	public Source() {}
	
	public Integer getId() {
		return id;
	}
	public void setId(Integer id) {
		this.id = id;
	}
	public int getUserId() {
		return userId;
	}
	public void setUserId(int userId) {
		this.userId = userId;
	}
	public String getUuid() {
		return uuid;
	}
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public boolean isDeleted() {
		return deleted;
	}
	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}
	public Date getCreated() {
		return created;
	}
	public void setCreated(Date created) {
		this.created = created;
	}
	public Date getModified() {
		return modified;
	}
	public void setModified(Date modified) {
		this.modified = modified;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	
	@Override
	public String toString() {
		return String.format("Source[id=%s, uuid=%s, name=%s, type=%s]", id, uuid, name, type);
	}

	//Convenience methods
	public boolean isAccount(){
		return "D".equals(getType()) || "C".equals(getType());
	}
}
