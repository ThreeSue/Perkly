import type { Merchant } from "@/types/discovery";

export type CartItem = { product: Merchant; quantity: number };

// 进程内演示购物车：主页添加商品，购物车页面读取和更新。
const items: CartItem[] = [];

export function addToCart(product: Merchant): number {
	const existing = items.find((item) => item.product.id === product.id);
	if (existing) {
		existing.quantity++;
	} else {
		items.push({ product: { ...product }, quantity: 1 });
	}
	return getCartCount();
}

export function getCartItems(): CartItem[] {
	return items.map((item) => ({ product: { ...item.product }, quantity: item.quantity }));
}

export function getCartCount(): number {
	return items.reduce((total, item) => total + item.quantity, 0);
}

export function setCartQuantity(productId: string, quantity: number): void {
	const item = items.find((entry) => entry.product.id === productId);
	if (item) item.quantity = Math.max(1, Math.floor(quantity));
}

export function removeFromCart(productId: string): void {
	const index = items.findIndex((item) => item.product.id === productId);
	if (index >= 0) items.splice(index, 1);
}
