import { useBase, checkPerm } from '/$/base';
export function mapPermission(action: string) {
	const { user } = useBase();
	return user.info?.username === 'admin' || checkPerm('merchant:' + action);
}
