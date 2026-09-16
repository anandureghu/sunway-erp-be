package com.erp.service;

import com.erp.assistant.AssistantOpenAiProperties;
import com.erp.assistant.OpenAiChatClient;
import com.erp.assistant.OpenAiChatClient.OpenAiChatResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeCsvColumnMapperService {

    public static final Set<String> CANONICAL_FIELDS = Set.of(
            "employeeNo", "firstName", "middleName", "lastName",
            "gender", "prefix", "maritalStatus", "dateOfBirth", "joinDate", "status",
            "birthplace", "hometown", "nationality", "religion", "identification",
            "phoneNo", "altPhone", "email",
            "departmentName", "companyRole",
            "designation", "probationEndDate", "reportingManagerNo", "workLocation",
            "bankName", "iban",
            "basicSalary", "housingAllowance", "transportAllowance", "otherAllowance"
    );

    private final OpenAiChatClient openAiChatClient;
    private final AssistantOpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public record MappingResult(Map<String, String> mapping, boolean aiMapped) {}

    public MappingResult mapHeaders(List<String> headers, List<List<String>> sampleRows) {
        if (headers == null || headers.isEmpty()) return new MappingResult(Map.of(), false);
        if (openAiProperties.isConfigured()) {
            try {
                Map<String, String> ai = mapWithOpenAi(headers, sampleRows);
                if (ai != null && !ai.isEmpty()) return new MappingResult(ai, true);
            } catch (Exception ex) {
                log.warn("OpenAI employee CSV mapping failed; falling back: {}", ex.getMessage());
            }
        }
        return new MappingResult(mapHeuristic(headers), false);
    }

    public static boolean isArabicHeader(String h) {
        if (h == null) return false;
        String n = h.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return n.endsWith("ar") || n.contains("arabic") || n.contains("عربي");
    }

    private Map<String, String> mapWithOpenAi(List<String> headers, List<List<String>> sampleRows) throws Exception {
        List<Map<String, String>> samples = new ArrayList<>();
        if (sampleRows != null) {
            for (List<String> row : sampleRows) {
                Map<String, String> sample = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++)
                    sample.put(headers.get(i), i < row.size() && row.get(i) != null ? row.get(i) : "");
                samples.add(sample);
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headers", headers);
        payload.put("sampleRows", samples);
        payload.put("canonicalFields", CANONICAL_FIELDS.stream().sorted().toList());

        String system = """
                You map CSV column headers from an employee master spreadsheet onto ERP fields.
                Return ONLY a JSON object: {"mapping":{"<source header>":"<canonicalField or null>",...}}
                Rules:
                - Every source header must appear as a key.
                - Value must be one of the canonicalFields, or null.
                - employeeNo = Employee No, Emp No, Staff No, Employee ID, Emp ID.
                - firstName = First Name, Given Name, Name (if no split).
                - middleName = Middle Name.
                - lastName = Last Name, Surname, Family Name.
                - gender = Gender, Sex.
                - prefix = Prefix, Title, Salutation (Mr/Ms/Dr).
                - maritalStatus = Marital Status, Marital.
                - dateOfBirth = Date of Birth, DOB, Birth Date (DD/MM/YYYY or YYYY-MM-DD).
                - joinDate = Join Date, Hire Date, Start Date, Date Joined (DD/MM/YYYY or YYYY-MM-DD).
                - status = Status, Employment Status (ACTIVE/INACTIVE/TERMINATED etc).
                - birthplace = Birthplace, Place of Birth.
                - hometown = Hometown, Home Town.
                - nationality = Nationality.
                - religion = Religion.
                - identification = ID No, ID Number, National ID, Identification.
                - phoneNo = Phone, Mobile, Phone No, Contact No.
                - altPhone = Alt Phone, Alternate Phone, Secondary Phone.
                - email = Email, Email Address.
                - departmentName = Department, Department Name, Dept.
                - companyRole = Company Role, Role, Position, Job Position.
                - designation = Designation, Job Title, Title (when used as job designation).
                - probationEndDate = Probation End Date, Probation End, Probation Until.
                - reportingManagerNo = Reporting Manager ID, Manager ID, Manager No, Reports To.
                - workLocation = Work Location, Location, Office Location.
                - bankName = Bank Name, Bank.
                - iban = IBAN, Bank Account, Account Number, IBAN No.
                - basicSalary = Basic Salary, Base Salary, Basic Pay, Basic (QAR).
                - housingAllowance = Housing Allowance, Housing, House Allowance.
                - transportAllowance = Transport Allowance, Transportation Allowance, Transport.
                - otherAllowance = Other Allowances, Other Allowance, Additional Allowance.
                - Never map Arabic/AR columns — always null.
                - Do not map two headers to the same canonical field.
                """;
        String user = "Map these CSV headers:\n" + objectMapper.writeValueAsString(payload);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        );
        OpenAiChatResult result = openAiChatClient.complete(messages, null, true);
        String content = result.content();
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < 0) return null;
        JsonNode root = objectMapper.readTree(content.substring(start, end + 1));
        JsonNode mappingNode = root.get("mapping");
        if (mappingNode == null || !mappingNode.isObject()) return null;
        Map<String, String> raw = objectMapper.convertValue(mappingNode, new TypeReference<>() {});
        Map<String, String> cleaned = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (String h : headers) {
            String val = raw.getOrDefault(h, null);
            if (val != null && (val.isBlank() || !CANONICAL_FIELDS.contains(val) || used.contains(val))) val = null;
            if (val != null) used.add(val);
            cleaned.put(h, val);
        }
        return cleaned;
    }

    private Map<String, String> mapHeuristic(List<String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (String header : headers) {
            if (header == null || header.isBlank() || isArabicHeader(header)) { result.put(header, null); continue; }
            String n = header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            String mapped = null;
            if ((n.contains("empno") || n.contains("employeeno") || n.contains("staffno") || n.contains("empid") || n.contains("employeeid")) && !used.contains("employeeNo")) mapped = "employeeNo";
            else if ((n.contains("firstname") || n.contains("givenname")) && !used.contains("firstName")) mapped = "firstName";
            else if (n.contains("middlename") && !used.contains("middleName")) mapped = "middleName";
            else if ((n.contains("lastname") || n.contains("surname") || n.contains("familyname")) && !used.contains("lastName")) mapped = "lastName";
            else if ((n.equals("gender") || n.equals("sex")) && !used.contains("gender")) mapped = "gender";
            else if ((n.equals("prefix") || n.equals("title") || n.equals("salutation")) && !used.contains("prefix")) mapped = "prefix";
            else if (n.contains("marital") && !used.contains("maritalStatus")) mapped = "maritalStatus";
            else if ((n.contains("dob") || n.contains("dateofbirth") || n.contains("birthdate")) && !used.contains("dateOfBirth")) mapped = "dateOfBirth";
            else if ((n.contains("joindate") || n.contains("hiredate") || n.contains("startdate") || n.contains("datejoined")) && !used.contains("joinDate")) mapped = "joinDate";
            else if ((n.equals("status") || n.contains("empstatus")) && !used.contains("status")) mapped = "status";
            else if ((n.contains("birthplace") || n.contains("placeofbirth")) && !used.contains("birthplace")) mapped = "birthplace";
            else if (n.contains("hometown") && !used.contains("hometown")) mapped = "hometown";
            else if (n.contains("nationality") && !used.contains("nationality")) mapped = "nationality";
            else if (n.equals("religion") && !used.contains("religion")) mapped = "religion";
            else if ((n.contains("idno") || n.contains("idnumber") || n.contains("nationalid") || n.equals("identification")) && !used.contains("identification")) mapped = "identification";
            else if ((n.contains("altphone") || n.contains("alternatephone") || n.contains("secondaryphone")) && !used.contains("altPhone")) mapped = "altPhone";
            else if ((n.contains("phone") || n.contains("mobile") || n.contains("contactno")) && !used.contains("phoneNo")) mapped = "phoneNo";
            else if (n.contains("email") && !used.contains("email")) mapped = "email";
            else if ((n.contains("dept") || n.contains("department")) && !used.contains("departmentName")) mapped = "departmentName";
            else if ((n.contains("companyrole") || n.contains("role") || n.equals("position")) && !used.contains("companyRole")) mapped = "companyRole";
            else if ((n.equals("designation") || n.contains("jobtitle")) && !used.contains("designation")) mapped = "designation";
            else if ((n.contains("probationend") || n.contains("probationuntil")) && !used.contains("probationEndDate")) mapped = "probationEndDate";
            else if ((n.contains("reportingmanager") || n.contains("managerid") || n.contains("managerno") || n.contains("reportsto")) && !used.contains("reportingManagerNo")) mapped = "reportingManagerNo";
            else if ((n.contains("worklocation") || n.equals("location")) && !used.contains("workLocation")) mapped = "workLocation";
            else if ((n.equals("bank") || n.contains("bankname")) && !used.contains("bankName")) mapped = "bankName";
            else if (n.equals("iban") && !used.contains("iban")) mapped = "iban";
            else if ((n.contains("basicsalary") || n.contains("basesalary") || n.contains("basicpay") || n.contains("basepay")) && !used.contains("basicSalary")) mapped = "basicSalary";
            else if ((n.contains("housingallowance") || n.equals("housing")) && !used.contains("housingAllowance")) mapped = "housingAllowance";
            else if ((n.contains("transportallowance") || n.contains("transportationallowance") || n.equals("transport")) && !used.contains("transportAllowance")) mapped = "transportAllowance";
            else if ((n.contains("otherallowance") || n.contains("otherallowances") || n.contains("additionalallowance")) && !used.contains("otherAllowance")) mapped = "otherAllowance";
            if (mapped != null) used.add(mapped);
            result.put(header, mapped);
        }
        return result;
    }
}
