export type MerchantPlace = { name: string; address: string; latitude: number; longitude: number };
export type MerchantAdmissionDetails = {
	merchantName: string;
	storeName: string;
	place: MerchantPlace | null;
};
export type MerchantAdmission = {
	applicationId: string;
	accountId: string;
	merchantId: string;
	version: number;
	status: "pending" | "approved" | "rejected";
	acknowledged: boolean;
	details: MerchantAdmissionDetails;
	submittedAt: string;
	reviewedAt?: string;
	rejectionReason?: string;
	history: {
		version: number;
		status: "pending" | "approved" | "rejected";
		submittedAt: string;
		reviewedAt?: string;
		rejectionReason?: string;
	}[];
};
