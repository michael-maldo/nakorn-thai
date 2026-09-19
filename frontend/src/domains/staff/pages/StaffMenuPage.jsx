import MenuAdminPage from '../../menu/pages/MenuAdminPage';

export default function StaffMenuPage({ hash }) {
  return <MenuAdminPage key={hash} hash={hash} />;
}
