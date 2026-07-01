package com.wornux.services.document;

import java.io.Serializable;
import java.util.List;

public record CourseMaterialCatalog(String label, String useWhen, List<String> aliases) implements Serializable {}
